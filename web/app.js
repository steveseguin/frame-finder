import { createEngine, loadLibrary } from './engines.js';

const $ = id => document.getElementById(id);
const video = $('video'), still = $('still'), iframe = $('vdo'), sample = $('sample');
const context = sample.getContext('2d', { willReadFrequently: true });
let stream, hls, objectUrl, sourceKind = '', sourceReady = false;
let generation = 0, running = false, paused = false, initializing = false, controller, engine, timer, busy;
let vdoOrigin = '', vdoStream = '', pendingFrame, count = 0, previous = '', repeats = 0;
let sourceWidth = 4, sourceHeight = 3;
let lastVideoTime = -1;

function status(message, error = false) { $('status').textContent = message; $('status').classList.toggle('error', error); }
function clearResult() {
  previous = ''; repeats = 0;
  $('item').textContent = 'Nothing identified yet';
  $('description').textContent = 'Hold one item still in good lighting.';
  $('candidates').replaceChildren();
  $('latency').textContent = '—';
  $('backend').textContent = '—';
  $('result-kind').textContent = 'CURRENT ITEM';
  $('sample-count').textContent = '0 frames analyzed';
  $('frame-note').textContent = 'A cropped preview will appear here.';
  context.clearRect(0, 0, sample.width, sample.height);
}
function controls() {
  $('start').disabled = running || initializing;
  $('hero-start').disabled = running || initializing;
  $('pause').disabled = !running || initializing;
  $('stop').disabled = !running && !initializing;
  for (const id of ['source', 'camera', 'engine', 'url', 'stream-id', 'file']) $(id).disabled = running || initializing;
  $('pause').textContent = paused ? 'Resume analysis' : 'Pause analysis';
  $('source-state').textContent = initializing ? 'STARTING' : running ? (paused ? 'PAUSED' : 'LIVE') : 'OFFLINE';
  $('live-dot').classList.toggle('active', running && !paused);
}
function sourceFields() {
  const kind = $('source').value;
  $('camera-field').hidden = kind !== 'camera';
  $('file-field').hidden = kind !== 'file';
  $('url-field').hidden = kind !== 'vdo' && kind !== 'hls';
  $('stream-field').hidden = kind !== 'vdo';
  $('start').textContent = kind === 'camera' ? 'Start camera' : kind === 'file' ? 'Analyze file' : 'Connect stream';
  $('hero-start').textContent = $('start').textContent + ' ↗';
  $('url').placeholder = kind === 'vdo' ? 'https://vdo.ninja/?view=your-stream' : 'https://example.com/live.m3u8';
}
async function cameras() {
  if (!navigator.mediaDevices?.enumerateDevices) return;
  const selected = $('camera').value;
  const devices = await navigator.mediaDevices.enumerateDevices();
  $('camera').replaceChildren(new Option('Default / rear camera', ''));
  devices.filter(d => d.kind === 'videoinput').forEach((d, i) => $('camera').add(new Option(d.label || 'Camera ' + (i + 1), d.deviceId)));
  $('camera').value = selected;
}
function focusBox() {
  const stage = $('stage');
  const scale = Math.min(stage.clientWidth / sourceWidth, stage.clientHeight / sourceHeight);
  const side = Math.min(sourceWidth, sourceHeight) * 0.8 * scale;
  Object.assign($('focus').style, { width: side + 'px', height: side + 'px', left: (stage.clientWidth - side) / 2 + 'px', top: (stage.clientHeight - side) / 2 + 'px' });
  $('focus').hidden = !sourceReady || !$('crop').checked || sourceKind === 'vdo';
}
function paintFrame(input, width, height) {
  sourceWidth = width; sourceHeight = height;
  const cropped = $('crop').checked;
  const side = Math.min(width, height) * 0.8;
  const sx = cropped ? (width - side) / 2 : 0, sy = cropped ? (height - side) / 2 : 0;
  const sw = cropped ? side : width, sh = cropped ? side : height;
  sample.width = Math.round(384 * sw / Math.max(sw, sh));
  sample.height = Math.round(384 * sh / Math.max(sw, sh));
  context.drawImage(input, sx, sy, sw, sh, 0, 0, sample.width, sample.height);
  focusBox();
}
function requestVdoFrame(signal) {
  return new Promise((resolve, reject) => {
    const cib = 'frame-finder-' + crypto.randomUUID();
    const cleanup = () => { clearTimeout(timeout); signal.removeEventListener('abort', cancel); pendingFrame = null; };
    const cancel = () => { cleanup(); reject(new DOMException('Stopped', 'AbortError')); };
    const timeout = setTimeout(() => { cleanup(); reject(new Error('No VDO.Ninja frame received. Check that the stream is live and the stream ID matches the publisher.')); }, 6000);
    pendingFrame = { cib, resolve: value => { cleanup(); resolve(value); } };
    signal.addEventListener('abort', cancel, { once: true });
    iframe.contentWindow.postMessage({ getVideoFrame: true, streamID: vdoStream, cib }, vdoOrigin);
  });
}
window.addEventListener('message', event => {
  if (!pendingFrame || event.source !== iframe.contentWindow || event.origin !== vdoOrigin) return;
  const data = event.data;
  if (!data || data.action !== 'image-frame-capture') return;
  const payload = data.value;
  if (payload?.cib !== pendingFrame.cib || payload.streamID !== vdoStream) return;
  if (!(payload.imageData instanceof Blob) || payload.imageData.size > 32 * 1024 * 1024) return;
  pendingFrame.resolve(payload.imageData);
});
function waitFor(element, eventName, signal) {
  return new Promise((resolve, reject) => {
    const cleanup = () => { clearTimeout(timeout); element.removeEventListener(eventName, done); element.removeEventListener('error', fail); signal.removeEventListener('abort', cancel); };
    const done = () => { cleanup(); resolve(); };
    const fail = () => { cleanup(); reject(new Error('Could not open this media. Check the format, URL and cross-origin access.')); };
    const cancel = () => { cleanup(); reject(new DOMException('Stopped', 'AbortError')); };
    const timeout = setTimeout(() => { cleanup(); reject(new Error('Timed out waiting for media. Check the stream URL or choose another file.')); }, 20000);
    element.addEventListener(eventName, done, { once: true });
    element.addEventListener('error', fail, { once: true });
    signal.addEventListener('abort', cancel, { once: true });
    if (signal.aborted) cancel();
  });
}
async function openSource(token, signal) {
  sourceKind = $('source').value;
  if (sourceKind === 'camera') {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('Camera access needs localhost or HTTPS. Start this folder with npm start and open http://localhost:8080.');
    const selected = $('camera').value;
    const acquired = await navigator.mediaDevices.getUserMedia({ audio: false, video: { ...(selected ? { deviceId: { exact: selected } } : { facingMode: { ideal: 'environment' } }), width: { ideal: 1280 }, height: { ideal: 720 } } });
    if (token !== generation) { acquired.getTracks().forEach(t => t.stop()); return; }
    stream = acquired;
    stream.getVideoTracks()[0].addEventListener('ended', () => { if (running) { stop(); status('Camera disconnected. Reconnect it and start again.', true); } });
    video.srcObject = stream;
    video.hidden = false;
    await video.play();
    await cameras();
  } else if (sourceKind === 'file') {
    const file = $('file').files[0];
    if (!file) throw new Error('Choose an image or video file first.');
    objectUrl = URL.createObjectURL(file);
    if (file.type.startsWith('image/')) {
      const ready = waitFor(still, 'load', signal);
      still.src = objectUrl; still.hidden = false;
      await ready;
    } else {
      video.src = objectUrl; video.loop = true; video.hidden = false;
      await video.play();
    }
  } else {
    let url;
    try { url = new URL($('url').value); } catch { throw new Error('Enter a full stream URL beginning with https://.'); }
    if (!['https:', 'http:'].includes(url.protocol)) throw new Error('Use an HTTP or HTTPS media URL.');
    if (sourceKind === 'vdo') {
      if (url.protocol !== 'https:' || !(url.hostname === 'vdo.ninja' || url.hostname.endsWith('.vdo.ninja'))) throw new Error('Use an HTTPS vdo.ninja viewer URL.');
      vdoStream = $('stream-id').value.trim() || url.searchParams.get('view') || '';
      if (!vdoStream || vdoStream.includes(',')) throw new Error('Choose one VDO.Ninja stream ID to analyze.');
      url.searchParams.set('view', vdoStream);
      url.searchParams.set('cleanoutput', ''); url.searchParams.set('autostart', '');
      url.searchParams.set('mute', ''); url.searchParams.set('iframetarget', location.origin);
      vdoOrigin = url.origin;
      const ready = waitFor(iframe, 'load', signal);
      iframe.src = url.href; iframe.hidden = false;
      await ready;
    } else {
      video.crossOrigin = 'anonymous'; video.hidden = false;
      if (url.pathname.endsWith('.m3u8') && !video.canPlayType('application/vnd.apple.mpegurl')) {
        await loadLibrary('hls');
        if (signal.aborted) return;
        if (!Hls.isSupported()) throw new Error('This browser cannot play this HLS stream. Try Safari or a current Chrome browser.');
        const ready = waitFor(video, 'loadeddata', signal);
        hls = new Hls({ enableWorker: false, lowLatencyMode: true });
        hls.on(Hls.Events.ERROR, (_, data) => { if (data.fatal) { stop(); status('HLS playback failed: ' + data.details + '. Check the stream and CORS headers.', true); } });
        hls.loadSource(url.href); hls.attachMedia(video);
        await ready;
      } else video.src = url.href;
      await video.play();
    }
  }
  if (token !== generation) return;
  sourceReady = true;
  $('empty').hidden = true;
  $('source-name').textContent = { camera: 'CAMERA PREVIEW', file: 'LOCAL FILE', vdo: 'VDO.NINJA STREAM', hls: 'REMOTE VIDEO' }[sourceKind];
  $('stage-caption').textContent = sourceKind === 'vdo' ? 'Sampling the selected stream · see analyzed frame' : 'One item at a time · keep it steady';
  if (!video.hidden) { sourceWidth = video.videoWidth || 4; sourceHeight = video.videoHeight || 3; }
  if (!still.hidden) { sourceWidth = still.naturalWidth; sourceHeight = still.naturalHeight; }
  focusBox();
}
function showResult(result, elapsed) {
  const candidates = result.candidates;
  const top = candidates[0];
  const usable = top && top.score >= Number($('threshold').value);
  const label = top ? (usable ? top.label : 'Unknown') : (result.label || 'Unknown');
  repeats = previous === label ? repeats + 1 : 1;
  previous = label;
  const unknown = /^unknown\b/i.test(label);
  $('item').textContent = unknown ? 'No clear match' : label;
  $('result-kind').textContent = unknown ? 'UNCERTAIN' : !still.hidden ? 'IMAGE ESTIMATE' : repeats >= 2 ? 'CONSISTENT ACROSS FRAMES' : 'CURRENT ESTIMATE';
  $('description').textContent = unknown ? 'Try a closer view, another angle, or the other recognition engine.' : result.description || 'Visual description · verify before using in a listing';
  $('candidates').replaceChildren();
  for (const candidate of candidates) {
    const row = document.createElement('div'); row.className = 'candidate';
    const labels = document.createElement('div');
    const name = document.createElement('span'); name.textContent = candidate.label;
    const value = document.createElement('span'); value.textContent = Math.round(candidate.score * 100) + '%';
    labels.append(name, value);
    const bar = document.createElement('div'); bar.className = 'bar';
    const fill = document.createElement('span'); fill.style.width = candidate.score * 100 + '%';
    bar.append(fill); row.append(labels, bar); $('candidates').append(row);
  }
  count++;
  $('sample-count').textContent = count + (count === 1 ? ' frame analyzed' : ' frames analyzed');
  $('latency').textContent = Math.round(elapsed) + ' ms';
  $('frame-note').textContent = new Date().toLocaleTimeString() + ' · ' + sample.width + ' × ' + sample.height;
  status(unknown ? 'No confident category in this frame.' : 'Analyzing locally · ' + (top ? 'scores are model estimates' : 'AI descriptions can be mistaken'));
}
async function analyze(token) {
  if (!running || paused || token !== generation || document.hidden) return;
  // An active check owns the next timer, including after pause/resume.
  if (busy) return;
  const signal = controller.signal;
  const currentEngine = engine;
  busy = (async () => {
    try {
      if (sourceKind === 'vdo') {
        const blob = await requestVdoFrame(signal);
        const bitmap = await createImageBitmap(blob);
        try { if (token !== generation || paused) return; paintFrame(bitmap, bitmap.width, bitmap.height); } finally { bitmap.close(); }
      } else if (!still.hidden) paintFrame(still, still.naturalWidth, still.naturalHeight);
      else {
        if (video.readyState < 2 || video.ended || video.currentTime === lastVideoTime) {
          $('result-kind').textContent = 'LAST RESULT · WAITING FOR VIDEO';
          previous = ''; repeats = 0;
          status(video.ended ? 'Video ended. Connect another stream or press Stop.' : 'Waiting for a new video frame…');
          return;
        }
        lastVideoTime = video.currentTime;
        paintFrame(video, video.videoWidth, video.videoHeight);
      }
      const start = performance.now();
      const result = await currentEngine.recognize(sample);
      if (running && !paused && token === generation && !document.hidden) showResult(result, performance.now() - start);
    } catch (error) {
      if (token !== generation || paused) return;
      previous = ''; repeats = 0;
      $('result-kind').textContent = 'NO CURRENT RESULT';
      $('item').textContent = 'Waiting for a clear frame';
      $('description').textContent = 'Waiting for the next usable frame.';
      $('latency').textContent = '—';
      $('candidates').replaceChildren();
      status(error.name === 'SecurityError' ? 'This video blocks frame access. Enable CORS on its server, or use a camera/local file.' : error.name === 'AbortError' ? 'AI took too long. Try Fast classifier or a longer check interval.' : error.message, true);
    }
  })();
  await busy;
  busy = null;
  if (running && !paused && !document.hidden && token === generation) timer = setTimeout(() => analyze(token), Number($('interval').value));
}
async function start() {
  if (running || initializing) return;
  const token = ++generation;
  controller = new AbortController();
  const signal = controller.signal;
  initializing = true; paused = false; count = 0; lastVideoTime = -1;
  clearResult(); controls(); status('Opening video source…');
  try {
    if (busy) await busy;
    if (token !== generation) return;
    // Initialize Nano while the start click still provides user activation.
    // Model preparation can be canceled before requesting a camera permission.
    const nextEngine = await createEngine($('engine').value, text => { if (token === generation) status(text); }, signal);
    if (token !== generation) { nextEngine.close(); return; }
    engine = nextEngine;
    $('backend').textContent = engine.backend;
    status('Opening video source… Allow camera access in the address bar if prompted.');
    await openSource(token, signal);
    if (token !== generation) return;
    initializing = false; running = true; controls();
    analyze(token);
  } catch (error) {
    if (token !== generation) return;
    stop();
    status(error.name === 'NotAllowedError' ? 'Camera permission was denied. Allow camera access in the address bar, or choose a file.' : error.name === 'NotFoundError' ? 'No camera found. Connect one or choose an image/video file.' : error.message, true);
  }
}
function stop() {
  generation++; running = false; initializing = false; paused = false;
  clearTimeout(timer); controller?.abort();
  const closing = engine; engine = null;
  if (closing) Promise.resolve(busy).finally(() => closing.close());
  stream?.getTracks().forEach(t => t.stop()); stream = null;
  hls?.destroy(); hls = null;
  video.pause(); video.srcObject = null; video.removeAttribute('src'); video.load();
  video.loop = false; video.removeAttribute('crossorigin');
  iframe.removeAttribute('src'); still.removeAttribute('src');
  if (objectUrl) URL.revokeObjectURL(objectUrl); objectUrl = null;
  video.hidden = true; iframe.hidden = true; still.hidden = true;
  sourceReady = false; $('empty').hidden = false; $('focus').hidden = true;
  $('stage-caption').textContent = 'Source stopped · No video is being analyzed';
  controls(); status('Stopped. Choose a source and start again.');
  if (count) $('result-kind').textContent = 'LAST RESULT · STOPPED';
}
$('start').onclick = start; $('hero-start').onclick = start; $('stop').onclick = stop;
$('pause').onclick = () => {
  paused = !paused; clearTimeout(timer); controls();
  previous = ''; repeats = 0;
  status(paused ? 'Analysis paused. The video preview is still running.' : 'Resuming analysis…');
  $('result-kind').textContent = paused ? 'LAST RESULT · PAUSED' : 'CURRENT ESTIMATE';
  if (!paused) analyze(generation);
};
$('source').onchange = sourceFields;
$('engine').onchange = () => {
  const nano = $('engine').value === 'nano';
  $('model-summary').textContent = nano ? 'Variable MB · Open-ended · Google terms' : '14.04 MB · 1,000 classes · Apache-2.0';
  $('model-summary').title = nano ? 'Chrome manages model size and download. No fixed class count. Google / Chrome terms apply. Tap for details.' : '14.04 MB model download · Apache-2.0 license · 1,000 ImageNet classes. Tap for details.';
  $('model-name').textContent = nano ? 'Gemini Nano (Chrome managed)' : 'MobileNet V2 1.0 / 224';
  $('model-size').textContent = nano ? 'Size varies with Chrome’s model version; no fixed MB value. Check chrome://on-device-internals on a supported desktop for the installed model. Download is separate from this demo.' : '14.04 MB (13.39 MiB), including weights and graph. Browser libraries and working memory are additional.';
  $('model-classes').textContent = nano ? 'Open-ended item descriptions; no fixed list or total number of classes. Requires supported desktop Chrome with image input. On phones, choose Fast classifier.' : '1,000 ImageNet categories. No custom product catalog or exact SKU matching.';
  $('model-license').textContent = nano ? 'Proprietary Google model, supplied under Google / Chrome terms. Not redistributed by this demo.' : 'Apache-2.0';
  $('model-source').href = nano ? 'https://developer.chrome.com/docs/ai/prompt-api' : 'https://www.kaggle.com/models/google/mobilenet-v2/tfJs/100-224-classification/2';
  $('engine-note').textContent = $('engine').value === 'nano' ? 'Describes visible items using Chrome’s on-device model. Requires supported desktop Chrome and may need a model download.' : 'Small image classifier. Best for common objects; try a mug, shoe, bottle, or keyboard.';
  $('engine-tag').textContent = $('engine').value === 'nano' ? 'BUILT-IN AI' : 'LOCAL';
};
$('crop').onchange = () => { previous = ''; repeats = 0; focusBox(); };
$('threshold').oninput = () => { $('threshold-value').textContent = Math.round($('threshold').value * 100) + '%'; };
document.addEventListener('visibilitychange', () => { clearTimeout(timer); if (!document.hidden && running && !paused) analyze(generation); });
window.addEventListener('pagehide', stop);
new ResizeObserver(focusBox).observe($('stage'));
sourceFields(); cameras().catch(() => {});
if (!window.isSecureContext) status('Use localhost or HTTPS to enable the camera and built-in AI.', true);
