import { mkdir, writeFile, rename } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { gunzipSync } from 'node:zlib';

// Optional offline setup. Downloaded files are served locally by npm start.
const web = fileURLToPath(new URL('./web/', import.meta.url));
const files = [
  ['vendor/tf.min.js', 'https://cdn.jsdelivr.net/npm/@tensorflow/tfjs@4.22.0/dist/tf.min.js'],
  ['vendor/mobilenet.min.js', 'https://cdn.jsdelivr.net/npm/@tensorflow-models/mobilenet@2.1.1/dist/mobilenet.min.js'],
  ['vendor/hls.min.js', 'https://cdn.jsdelivr.net/npm/hls.js@1.5.18/dist/hls.min.js'],
  ['vendor/LICENSE-tensorflow.txt', 'https://raw.githubusercontent.com/tensorflow/tfjs/tfjs-v4.22.0/LICENSE'],
  ['vendor/LICENSE-mobilenet.txt', 'https://raw.githubusercontent.com/tensorflow/tfjs-models/mobilenet-v2.1.1/LICENSE'],
  ['vendor/LICENSE-hls.txt', 'https://cdn.jsdelivr.net/npm/hls.js@1.5.18/LICENSE']
];
const manifest = [];
async function download(url) {
  console.log('Downloading ' + url);
  const response = await fetch(url, { signal: AbortSignal.timeout(120000) });
  if (!response.ok) throw new Error(`${response.status} downloading ${url}`);
  const bytes = Buffer.from(await response.arrayBuffer());
  return { bytes, url };
}
function readModelArchive(bytes) {
  const tar = gunzipSync(bytes, { maxOutputLength: 64 * 1024 * 1024 });
  const entries = new Map();
  for (let offset = 0; offset + 512 <= tar.length;) {
    const header = tar.subarray(offset, offset + 512);
    const name = header.subarray(0, 100).toString('utf8').replace(/\0.*$/, '').replace(/^\.\//, '');
    if (!name) break;
    const size = parseInt(header.subarray(124, 136).toString('ascii').replace(/\0.*$/, '').trim(), 8);
    if (!Number.isSafeInteger(size) || size < 0 || offset + 512 + size > tar.length) throw new Error('Invalid model archive entry');
    // Accept only model data, never links or executable archive entries.
    if ((header[156] === 0 || header[156] === 48) && /^(model\.json|[\w-]+\.bin)$/.test(name)) {
      entries.set(name, tar.subarray(offset + 512, offset + 512 + size));
    }
    offset += 512 + Math.ceil(size / 512) * 512;
  }
  return entries;
}
async function save(name, downloaded) {
  const destination = path.resolve(web, name);
  if (!destination.startsWith(web)) throw new Error('Invalid output path');
  await mkdir(path.dirname(destination), { recursive: true });
  await writeFile(destination + '.part', downloaded.bytes);
  await rename(destination + '.part', destination);
  manifest.push({ file: name, source: downloaded.url, bytes: downloaded.bytes.length, sha256: createHash('sha256').update(downloaded.bytes).digest('hex') });
}
try {
  // Sequential transfers keep resource use bounded.
  for (const [name, url] of files) await save(name, await download(url));
  const modelSource = 'https://www.kaggle.com/api/v1/models/google/mobilenet-v2/tfJs/100-224-classification/2/download';
  const downloaded = await download(modelSource);
  const entries = readModelArchive(downloaded.bytes);
  const model = JSON.parse(entries.get('model.json')?.toString('utf8') || 'null');
  if (!model?.modelTopology || !Array.isArray(model.weightsManifest)) throw new Error('The model publisher did not return a TensorFlow.js model.');
  const shards = new Map();
  for (const group of model.weightsManifest) {
    group.paths = group.paths.map(original => {
      if (!entries.has(original)) throw new Error('Missing model weight: ' + original);
      if (!shards.has(original)) shards.set(original, 'weights-' + shards.size + '.bin');
      return shards.get(original);
    });
  }
  for (const [source, name] of shards) await save('models/mobilenet/' + name, { url: modelSource + '#' + source, bytes: entries.get(source) });
  // Publish model.json last, so an interrupted setup cannot expose partial weights.
  await save('models/mobilenet/model.json', { url: downloaded.url, bytes: Buffer.from(JSON.stringify(model)) });
  await writeFile(path.join(web, 'assets-manifest.json'), JSON.stringify(manifest, null, 2) + '\n');
  console.log('Ready. Copy this entire folder to your Mac. Camera/file classification now works without CDN or model downloads.');
} catch (error) {
  console.error('Asset setup failed: ' + error.message);
  console.error('Check your connection and run npm run download-models again. Online browser mode can also load assets directly.');
  process.exitCode = 1;
}
