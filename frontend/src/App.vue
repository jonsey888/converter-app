<script setup>
import { ref, computed, watch } from 'vue'

const CONFIGS = {
  'csv-json':  { inputLabel: 'CSV Input',      outputLabel: 'JSON Output',    inputType: 'text',  outputType: 'text', ext: 'json', mime: 'application/json' },
  'json-csv':  { inputLabel: 'JSON Input',     outputLabel: 'CSV Output',     inputType: 'text',  outputType: 'text', ext: 'csv',  mime: 'text/csv' },
  'md-html':   { inputLabel: 'Markdown Input', outputLabel: 'HTML Preview',   inputType: 'text',  outputType: 'html', ext: 'html', mime: 'text/html' },
  'json-yaml': { inputLabel: 'JSON Input',     outputLabel: 'YAML Output',    inputType: 'text',  outputType: 'text', ext: 'yaml', mime: 'text/yaml' },
  'yaml-json': { inputLabel: 'YAML Input',     outputLabel: 'JSON Output',    inputType: 'text',  outputType: 'text', ext: 'json', mime: 'application/json' },
  'json-xml':  { inputLabel: 'JSON Input',     outputLabel: 'XML Output',     inputType: 'text',  outputType: 'text', ext: 'xml',  mime: 'application/xml' },
  'xml-json':  { inputLabel: 'XML Input',      outputLabel: 'JSON Output',    inputType: 'text',  outputType: 'text', ext: 'json', mime: 'application/json' },
  'json-toml': { inputLabel: 'JSON Input',     outputLabel: 'TOML Output',    inputType: 'text',  outputType: 'text', ext: 'toml', mime: 'text/plain' },
  'toml-json': { inputLabel: 'TOML Input',     outputLabel: 'JSON Output',    inputType: 'text',  outputType: 'text', ext: 'json', mime: 'application/json' },
  'img-conv':  { inputLabel: 'Source Image',   outputLabel: 'Converted Image',inputType: 'image', outputType: 'image',ext: 'jpg',  mime: 'image/jpeg' },
}

const converterType  = ref('csv-json')
const inputText      = ref('')
const outputText     = ref('')
const outputHtml     = ref('')
const inputImageFile = ref(null)
const inputImageUrl  = ref(null)
const outputImageUrl = ref(null)
const imgFormat      = ref('jpeg')
const imgQuality     = ref(0.92)
const isLoading      = ref(false)
const errorMsg       = ref('')
const isDone         = ref(false)
const isDragging     = ref(false)
const dragCount      = ref(0)
const imageFileInput = ref(null)

const cfg       = computed(() => CONFIGS[converterType.value])
const isImgMode = computed(() => cfg.value.inputType  === 'image')
const isHtmlOut = computed(() => cfg.value.outputType === 'html')
const isImgOut  = computed(() => cfg.value.outputType === 'image')

watch(converterType, clearAll)

// ── Drag & drop ──────────────────────────────────────────────────────────────

function onDragEnter() { dragCount.value++ }
function onDragLeave() { dragCount.value-- }
function onDrop(e) {
  e.preventDefault()
  dragCount.value = 0
  const file = e.dataTransfer.files[0]
  if (!file) return
  if (isImgMode.value) {
    if (file.type.startsWith('image/')) loadImageFile(file)
    else errorMsg.value = 'Please drop an image file.'
  } else {
    const reader = new FileReader()
    reader.onload = ev => { inputText.value = ev.target.result }
    reader.readAsText(file)
  }
}

// ── Image handling ────────────────────────────────────────────────────────────

function triggerImageInput() { imageFileInput.value.click() }

function onImageFileChange(e) {
  const file = e.target.files[0]
  if (file) loadImageFile(file)
  e.target.value = ''
}

function loadImageFile(file) {
  if (inputImageUrl.value) URL.revokeObjectURL(inputImageUrl.value)
  inputImageFile.value = file
  inputImageUrl.value  = URL.createObjectURL(file)
}

// ── Convert ───────────────────────────────────────────────────────────────────

async function handleConvert() {
  errorMsg.value = ''
  isDone.value   = false
  isLoading.value = true
  try {
    if (isImgMode.value) {
      if (!inputImageFile.value) throw new Error('No image selected')
      const form = new FormData()
      form.append('file', inputImageFile.value)
      form.append('format', imgFormat.value)
      form.append('quality', imgQuality.value.toString())
      const res = await fetch('/api/convert/image', { method: 'POST', body: form })
      if (!res.ok) throw new Error('Image conversion failed')
      const blob = await res.blob()
      if (outputImageUrl.value) URL.revokeObjectURL(outputImageUrl.value)
      outputImageUrl.value = URL.createObjectURL(blob)
    } else {
      if (!inputText.value.trim()) throw new Error('Input is empty')
      const res = await fetch('/api/convert/text', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ type: converterType.value, input: inputText.value }),
      })
      const data = await res.json()
      if (!res.ok) throw new Error(data.error || 'Conversion failed')
      outputText.value = data.output
      outputHtml.value = isHtmlOut.value ? data.output : ''
    }
    isDone.value = true
  } catch (err) {
    errorMsg.value = err.message
  } finally {
    isLoading.value = false
  }
}

// ── Download ──────────────────────────────────────────────────────────────────

function handleDownload() {
  if (!isDone.value) return
  if (isImgOut.value) {
    if (!outputImageUrl.value) return
    const ext = imgFormat.value === 'jpeg' ? 'jpg' : imgFormat.value
    triggerAnchorDownload(outputImageUrl.value, `converted.${ext}`)
    return
  }
  const blob = new Blob([outputText.value], { type: cfg.value.mime + ';charset=utf-8' })
  const url  = URL.createObjectURL(blob)
  triggerAnchorDownload(url, `converted.${cfg.value.ext}`)
  URL.revokeObjectURL(url)
}

function triggerAnchorDownload(href, filename) {
  const a = document.createElement('a')
  a.href = href; a.download = filename; a.click()
}

// ── Clear ──────────────────────────────────────────────────────────────────────

function clearAll() {
  inputText.value   = ''
  outputText.value  = ''
  outputHtml.value  = ''
  inputImageFile.value = null
  if (inputImageUrl.value)  { URL.revokeObjectURL(inputImageUrl.value);  inputImageUrl.value  = null }
  if (outputImageUrl.value) { URL.revokeObjectURL(outputImageUrl.value); outputImageUrl.value = null }
  errorMsg.value  = ''
  isDone.value    = false
  dragCount.value = 0
}
</script>

<template>
  <div class="app">
    <!-- Header -->
    <header>
      <h1>⚡ File Format Converter</h1>
      <div class="select-wrap">
        <label for="type">Convert:</label>
        <select id="type" v-model="converterType">
          <optgroup label="Table">
            <option value="csv-json">CSV → JSON</option>
            <option value="json-csv">JSON → CSV</option>
          </optgroup>
          <optgroup label="Markup">
            <option value="md-html">Markdown → HTML</option>
          </optgroup>
          <optgroup label="Structured Data">
            <option value="json-yaml">JSON → YAML</option>
            <option value="yaml-json">YAML → JSON</option>
            <option value="json-xml">JSON → XML</option>
            <option value="xml-json">XML → JSON</option>
            <option value="json-toml">JSON → TOML</option>
            <option value="toml-json">TOML → JSON</option>
          </optgroup>
          <optgroup label="Image">
            <option value="img-conv">Image Conversion (PNG/JPG/WebP)</option>
          </optgroup>
        </select>
      </div>
    </header>

    <main>
      <div class="panels">
        <!-- Input Panel -->
        <div class="panel">
          <div class="panel-header">{{ cfg.inputLabel }}</div>
          <div
            class="drop-zone"
            :class="{ 'drag-over': dragCount > 0 }"
            @dragover.prevent
            @dragenter.prevent="onDragEnter"
            @dragleave="onDragLeave"
            @drop="onDrop"
          >
            <!-- Text input -->
            <template v-if="!isImgMode">
              <div class="drop-hint" v-if="!inputText">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <path d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12"/>
                </svg>
                Drag &amp; drop file here
              </div>
              <textarea
                v-model="inputText"
                placeholder="Paste content here…"
                spellcheck="false"
              />
            </template>

            <!-- Image input -->
            <template v-else>
              <div v-if="!inputImageUrl" class="image-input-area" @click="triggerImageInput">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                  <rect x="3" y="3" width="18" height="18" rx="2"/>
                  <circle cx="8.5" cy="8.5" r="1.5"/>
                  <path d="M21 15l-5-5L5 21"/>
                </svg>
                <span>Click or drag an image here</span>
                <span class="hint-sub">PNG, JPG, WebP supported</span>
              </div>
              <div v-else class="image-preview-wrap">
                <img :src="inputImageUrl" alt="Input image" />
                <button class="change-btn" @click="triggerImageInput">Change</button>
              </div>
            </template>
          </div>
        </div>

        <!-- Output Panel -->
        <div class="panel">
          <div class="panel-header">
            {{ cfg.outputLabel }}
            <span class="status-tag" v-if="isDone">Done ✓</span>
          </div>

          <textarea
            v-if="!isHtmlOut && !isImgOut"
            :value="outputText"
            readonly
            placeholder="Result will appear here…"
            spellcheck="false"
          />
          <div v-else-if="isHtmlOut" class="html-preview" v-html="outputHtml" />
          <div v-else-if="isImgOut" class="image-preview-wrap">
            <img v-if="outputImageUrl" :src="outputImageUrl" alt="Output image" />
            <span v-else class="empty-img-hint">Converted image will appear here</span>
          </div>

          <!-- Image format options -->
          <div v-if="isImgMode" class="img-options">
            <label>
              Format:
              <select v-model="imgFormat">
                <option value="jpeg">JPG</option>
                <option value="png">PNG</option>
                <option value="webp">WebP</option>
              </select>
            </label>
            <label>
              Quality: {{ imgQuality.toFixed(2) }}
              <input type="range" v-model.number="imgQuality" min="0.1" max="1" step="0.01" />
            </label>
          </div>
        </div>
      </div>

      <!-- Error -->
      <div class="error-msg" v-if="errorMsg">{{ errorMsg }}</div>

      <!-- Actions -->
      <div class="actions">
        <button class="btn-convert" :disabled="isLoading" @click="handleConvert">
          {{ isLoading ? 'Converting…' : 'Convert' }}
        </button>
        <button class="btn-download" :disabled="!isDone" @click="handleDownload">
          Download Result
        </button>
        <button class="btn-clear" @click="clearAll">Clear</button>
      </div>
    </main>

    <footer>Java (Spring Boot) backend · Vue 3 frontend · No data stored on server</footer>

    <input
      type="file"
      ref="imageFileInput"
      accept="image/*"
      style="display:none"
      @change="onImageFileChange"
    />
  </div>
</template>

<style>
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #f5f7fa;
  color: #1a1a2e;
  min-height: 100vh;
}
</style>

<style scoped>
.app {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
}

header {
  background: #fff;
  border-bottom: 1px solid #e2e8f0;
  padding: 14px 24px;
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

header h1 {
  font-size: 1.2rem;
  font-weight: 700;
  color: #2d3748;
  white-space: nowrap;
}

.select-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.select-wrap label {
  font-size: 0.875rem;
  color: #718096;
  font-weight: 500;
}

select {
  padding: 7px 11px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  font-size: 0.875rem;
  background: #fff;
  color: #2d3748;
  cursor: pointer;
  outline: none;
  transition: border-color 0.2s;
}

select:focus { border-color: #667eea; }

main {
  flex: 1;
  padding: 20px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 680px) {
  .panels { grid-template-columns: 1fr; }
}

.panel {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 320px;
}

.panel-header {
  padding: 10px 16px;
  font-size: 0.78rem;
  font-weight: 600;
  color: #718096;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  border-bottom: 1px solid #f0f4f8;
  background: #fafbfc;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.drop-zone {
  flex: 1;
  display: flex;
  flex-direction: column;
  position: relative;
  transition: background 0.15s;
}

.drop-zone.drag-over { background: #ebf4ff; }

.drop-hint {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: #a0aec0;
  pointer-events: none;
  font-size: 0.875rem;
}

.drop-hint svg { width: 36px; height: 36px; opacity: 0.5; }

textarea {
  flex: 1;
  border: none;
  outline: none;
  padding: 14px 16px;
  font-family: 'SF Mono', 'Fira Code', 'Consolas', monospace;
  font-size: 0.8125rem;
  line-height: 1.6;
  resize: none;
  background: transparent;
  color: #2d3748;
}

textarea::placeholder { color: #cbd5e0; }
textarea[readonly] { background: #fafbfc; }

.image-input-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 20px;
  cursor: pointer;
  color: #a0aec0;
  font-size: 0.875rem;
  transition: background 0.15s;
}

.image-input-area:hover { background: #f8faff; }
.image-input-area svg { width: 48px; height: 48px; opacity: 0.4; }
.hint-sub { font-size: 0.75rem; }

.image-preview-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px;
  overflow: hidden;
}

.image-preview-wrap img {
  max-width: 100%;
  max-height: 260px;
  border-radius: 8px;
  object-fit: contain;
}

.change-btn {
  padding: 5px 14px;
  font-size: 0.8rem;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  color: #4a5568;
}

.change-btn:hover { background: #f7fafc; }

.empty-img-hint {
  color: #cbd5e0;
  font-size: 0.875rem;
}

.html-preview {
  flex: 1;
  padding: 14px 16px;
  overflow: auto;
  font-size: 0.875rem;
  line-height: 1.7;
}

.html-preview :deep(h1),
.html-preview :deep(h2),
.html-preview :deep(h3) { margin: 0.5em 0; font-weight: 600; }
.html-preview :deep(p)  { margin: 0.4em 0; }
.html-preview :deep(code) { background: #f0f4f8; padding: 1px 5px; border-radius: 3px; font-size: 0.85em; font-family: monospace; }
.html-preview :deep(pre)  { background: #f0f4f8; padding: 10px; border-radius: 6px; overflow-x: auto; margin: 0.5em 0; }
.html-preview :deep(ul),
.html-preview :deep(ol)   { padding-left: 1.4em; margin: 0.4em 0; }
.html-preview :deep(blockquote) { border-left: 3px solid #cbd5e0; padding-left: 12px; color: #718096; margin: 0.4em 0; }
.html-preview :deep(a)    { color: #667eea; }
.html-preview :deep(table) { border-collapse: collapse; }
.html-preview :deep(th),
.html-preview :deep(td)    { border: 1px solid #e2e8f0; padding: 5px 10px; }
.html-preview :deep(th)    { background: #f7fafc; }

.img-options {
  padding: 10px 16px;
  border-top: 1px solid #f0f4f8;
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  font-size: 0.8125rem;
  color: #718096;
}

.img-options label {
  display: flex;
  align-items: center;
  gap: 6px;
}

.img-options select { padding: 4px 8px; font-size: 0.8125rem; }
.img-options input[type=range] { width: 80px; accent-color: #667eea; }

.error-msg {
  color: #e53e3e;
  font-size: 0.8125rem;
  padding: 8px 14px;
  background: #fff5f5;
  border: 1px solid #fed7d7;
  border-radius: 8px;
}

.status-tag {
  font-size: 0.7rem;
  padding: 2px 8px;
  border-radius: 10px;
  background: #e9f5f0;
  color: #2f855a;
  font-weight: 600;
}

.actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
}

button {
  padding: 10px 22px;
  border: none;
  border-radius: 8px;
  font-size: 0.875rem;
  font-weight: 600;
  cursor: pointer;
  transition: filter 0.15s, transform 0.1s;
}

button:active { transform: scale(0.97); }
button:disabled { opacity: 0.45; cursor: not-allowed; transform: none; }

.btn-convert {
  background: linear-gradient(135deg, #667eea, #764ba2);
  color: #fff;
}

.btn-convert:hover:not(:disabled) { filter: brightness(1.08); }

.btn-download {
  background: #edf2f7;
  color: #4a5568;
}

.btn-download:hover:not(:disabled) { background: #e2e8f0; }

.btn-clear {
  background: #fff5f5;
  color: #e53e3e;
}

.btn-clear:hover { background: #fed7d7; }

footer {
  text-align: center;
  padding: 12px;
  font-size: 0.75rem;
  color: #a0aec0;
  border-top: 1px solid #e2e8f0;
  background: #fff;
}
</style>
