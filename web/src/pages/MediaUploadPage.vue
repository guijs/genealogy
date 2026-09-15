<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  requestUploadUrl,
  putUploadFile,
  validateFile,
  MediaApiError,
  ALLOWED_MIME_TYPES,
} from '../features/tree/api/mediaClient'
import { isUsingGraphApi } from '../features/tree/api/graphClient'

const route = useRoute()
const router = useRouter()

const familyId = computed(() => (route.query.familyId as string) || '')
const usingGraphApi = computed(() => isUsingGraphApi())

const selectedFile = ref<File | null>(null)
const uploading = ref(false)
const uploadStep = ref<'idle' | 'getting-url' | 'uploading' | 'success'>('idle')
const errorMessage = ref('')
const uploadUrl = ref('')
const storageKey = ref('')
const previewUrl = ref<string | null>(null)

const fileInputRef = ref<HTMLInputElement | null>(null)

onUnmounted(() => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
})

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function handleFileSelect(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  
  errorMessage.value = ''
  uploadUrl.value = ''
  storageKey.value = ''
  uploadStep.value = 'idle'
  
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
  }
  
  if (!file) {
    selectedFile.value = null
    return
  }
  
  try {
    validateFile(file.type, file.size)
    selectedFile.value = file
  } catch (e) {
    selectedFile.value = null
    if (e instanceof MediaApiError) {
      errorMessage.value = e.message
    } else {
      errorMessage.value = '文件验证失败'
    }
  }
}

async function handleUpload() {
  if (!selectedFile.value || !familyId.value || uploading.value) return
  
  uploading.value = true
  errorMessage.value = ''
  uploadUrl.value = ''
  storageKey.value = ''
  uploadStep.value = 'getting-url'
  
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
  }
  
  try {
    const response = await requestUploadUrl(
      familyId.value,
      selectedFile.value.type,
      selectedFile.value.size,
    )
    uploadUrl.value = response.upload_url
    storageKey.value = response.storage_key
    
    uploadStep.value = 'uploading'
    await putUploadFile(response.upload_url, selectedFile.value)
    
    uploadStep.value = 'success'
    previewUrl.value = URL.createObjectURL(selectedFile.value)
  } catch (e) {
    uploadStep.value = 'idle'
    if (e instanceof MediaApiError) {
      errorMessage.value = e.message
    } else if (e instanceof Error) {
      errorMessage.value = e.message
    } else {
      errorMessage.value = '上传失败'
    }
  } finally {
    uploading.value = false
  }
}

function clearSelection() {
  selectedFile.value = null
  uploadUrl.value = ''
  storageKey.value = ''
  errorMessage.value = ''
  uploadStep.value = 'idle'
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = null
  }
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
}

function goBack() {
  if (familyId.value) {
    router.push({ name: 'tree', query: { familyId: familyId.value } })
  } else {
    router.push({ name: 'tree' })
  }
}
</script>

<template>
  <div class="page">
    <header class="topbar">
      <div>
        <h1>媒体上传</h1>
        <p class="sub">
          <template v-if="usingGraphApi">
            已接真 API · 选择图片后直接上传
          </template>
          <template v-else>
            当前为 mock 模式 · 上传功能不可用
          </template>
        </p>
      </div>
      <button type="button" class="btn-back" @click="goBack">
        返回家族树
      </button>
    </header>

    <main class="content">
      <div v-if="!familyId" class="warning-box">
        <p>未指定家族 ID。请从家族树页面进入。</p>
        <button type="button" class="btn-primary" @click="goBack">
          返回家族树
        </button>
      </div>

      <div v-else-if="!usingGraphApi" class="warning-box">
        <p>当前为 mock 模式，上传媒体文件需要连接真实 API。</p>
        <p class="sub-text">请设置 <code>VITE_USE_GRAPH_API=true</code> 或 <code>VITE_GRAPH_API_BASE</code></p>
      </div>

      <div v-else class="upload-card">
        <h2>选择图片文件</h2>
        <p class="hint">
          支持格式：JPEG、PNG、WebP · 最大 5MB
        </p>

        <div class="file-input-area">
          <input
            ref="fileInputRef"
            type="file"
            :accept="ALLOWED_MIME_TYPES.join(',')"
            class="file-input"
            @change="handleFileSelect"
          />
        </div>

        <div v-if="selectedFile" class="file-info">
          <p><strong>文件名：</strong>{{ selectedFile.name }}</p>
          <p><strong>类型：</strong>{{ selectedFile.type }}</p>
          <p><strong>大小：</strong>{{ formatFileSize(selectedFile.size) }}</p>
        </div>

        <div v-if="errorMessage" class="error-box" role="alert">
          {{ errorMessage }}
        </div>

        <div class="actions">
          <button
            type="button"
            class="btn-primary"
            :disabled="!selectedFile || uploading || uploadStep === 'success'"
            @click="handleUpload"
          >
            <template v-if="uploadStep === 'getting-url'">获取上传地址…</template>
            <template v-else-if="uploadStep === 'uploading'">上传中…</template>
            <template v-else-if="uploadStep === 'success'">已上传</template>
            <template v-else>上传文件</template>
          </button>
          <button
            v-if="selectedFile"
            type="button"
            class="btn-secondary"
            :disabled="uploading"
            @click="clearSelection"
          >
            清除
          </button>
        </div>

        <div v-if="uploadStep === 'success' && storageKey" class="result-box success">
          <h3>上传成功</h3>
          <div v-if="previewUrl" class="preview-area">
            <img :src="previewUrl" alt="预览" class="preview-image" />
          </div>
          <div class="result-item">
            <label>存储标识 (storage_key)</label>
            <code class="result-value">{{ storageKey }}</code>
          </div>
          <p class="result-hint success-hint">
            文件已成功上传到服务器
          </p>
        </div>
      </div>
    </main>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  font-family: var(--font-cn, sans-serif);
  background: var(--color-paper, #faf9f7);
  color: var(--color-ink, #1f1f1f);
}

.topbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-bottom: 1px solid var(--color-border, #d8d4cc);
  background: #fff;
}

.topbar h1 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #666;
}

.sub code {
  font-size: 12px;
  background: #f3f1ec;
  padding: 1px 4px;
  border-radius: 4px;
}

.btn-back {
  padding: 6px 14px;
  border: 1px solid var(--color-accent, #2f5d50);
  border-radius: 6px;
  background: #fff;
  color: var(--color-accent, #2f5d50);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
}

.btn-back:hover {
  background: #f8f7f5;
}

.content {
  flex: 1;
  padding: 24px;
  max-width: 600px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
}

.warning-box {
  background: #fff4e5;
  border: 1px solid #e6a23c;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
}

.warning-box p {
  margin: 0 0 12px;
  font-size: 15px;
}

.warning-box .sub-text {
  font-size: 13px;
  color: #8a6d3b;
}

.upload-card {
  background: #fff;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 12px;
  padding: 24px;
}

.upload-card h2 {
  margin: 0 0 8px;
  font-size: 18px;
  font-weight: 600;
}

.hint {
  margin: 0 0 20px;
  font-size: 14px;
  color: #666;
}

.file-input-area {
  margin-bottom: 16px;
}

.file-input {
  width: 100%;
  padding: 12px;
  border: 2px dashed var(--color-border, #d8d4cc);
  border-radius: 8px;
  background: #faf9f7;
  font-size: 14px;
  cursor: pointer;
}

.file-input:hover {
  border-color: var(--color-accent, #2f5d50);
}

.file-info {
  background: #f8f7f5;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 16px;
  font-size: 14px;
}

.file-info p {
  margin: 4px 0;
}

.error-box {
  background: #fef0f0;
  border: 1px solid #f5c6cb;
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 16px;
  color: #c53030;
  font-size: 14px;
}

.actions {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.btn-primary {
  flex: 1;
  padding: 12px 16px;
  border: none;
  border-radius: 6px;
  background: var(--color-accent, #2f5d50);
  color: #fff;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary:hover:not(:disabled) {
  opacity: 0.9;
}

.btn-primary:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 12px 16px;
  border: 1px solid var(--color-border, #d8d4cc);
  border-radius: 6px;
  background: #fff;
  font-size: 15px;
  cursor: pointer;
}

.btn-secondary:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.result-box {
  background: #f0fff4;
  border: 1px solid #9ae6b4;
  border-radius: 8px;
  padding: 16px;
}

.result-box h3 {
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
  color: #276749;
}

.result-item {
  margin-bottom: 12px;
}

.result-item label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #555;
  margin-bottom: 4px;
}

.result-value {
  display: block;
  background: #fff;
  border: 1px solid #d8d4cc;
  border-radius: 4px;
  padding: 8px 10px;
  font-size: 13px;
  word-break: break-all;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.result-hint {
  margin: 12px 0 0;
  font-size: 13px;
  color: #666;
}

.success-hint {
  color: #276749;
}

.preview-area {
  margin-bottom: 16px;
  text-align: center;
}

.preview-image {
  max-width: 100%;
  max-height: 200px;
  border-radius: 8px;
  border: 1px solid #d8d4cc;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.result-box.success {
  background: #f0fff4;
  border-color: #38a169;
}
</style>
