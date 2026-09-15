import * as React from "react";

import { ArrowUp, ImagePlus, Loader2, Square, X } from "lucide-react";
import { toast } from "sonner";

import { awaitingConfirm, useAgentChatStore } from "@/stores/agentChatStore";
import { uploadAgentImage } from "@/services/agentService";

// 无深度思考开关：Agent 自主规划是否思考
export function AgentChatInput() {
  const [value, setValue] = React.useState("");
  const isComposingRef = React.useRef(false);
  const textareaRef = React.useRef<HTMLTextAreaElement | null>(null);
  const fileRef = React.useRef<HTMLInputElement | null>(null);
  const [image, setImage] = React.useState<{ file: File; preview: string } | null>(null);
  const [uploading, setUploading] = React.useState(false);
  const { sendMessage, isStreaming, cancelGeneration, inputFocusKey, draft, messages } =
    useAgentChatStore();
  // 挂起在写操作确认上时输入条让位：这会儿只有卡片上那两枚钮是有效的下一步
  const awaiting = awaitingConfirm(messages);

  const focusInput = React.useCallback(() => {
    textareaRef.current?.focus({ preventScroll: true });
  }, []);

  const adjustHeight = React.useCallback(() => {
    const el = textareaRef.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.min(el.scrollHeight, 160)}px`;
  }, []);

  React.useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  React.useEffect(() => {
    if (!inputFocusKey) return;
    focusInput();
  }, [inputFocusKey, focusInput]);

  // 空态示例问题：预填并聚焦 不直接发送
  React.useEffect(() => {
    if (!draft) return;
    setValue(draft.text);
    focusInput();
  }, [draft, focusInput]);

  const submit = async () => {
    if ((!value.trim() && !image) || isStreaming || awaiting || uploading) return;
    const next = value;
    try {
      setUploading(true);
      const uploaded = image ? await uploadAgentImage(image.file) : undefined;
      setValue("");
      if (image) URL.revokeObjectURL(image.preview);
      setImage(null);
      await sendMessage(next, uploaded ? { attachmentId: uploaded.attachmentId, filename: uploaded.filename } : undefined);
    } catch (error) {
      toast.error((error as Error).message || "图片上传失败");
    } finally {
      setUploading(false);
      focusInput();
    }
  };

  return (
    <div className="agent-composer">
      {image ? (
        <div className="agent-composer-attachment">
          <img src={image.preview} alt="待发送图片" />
          <span title={image.file.name}>{image.file.name}</span>
          <button type="button" onClick={() => { URL.revokeObjectURL(image.preview); setImage(null); }} aria-label="移除图片">
            <X className="h-3.5 w-3.5" />
          </button>
        </div>
      ) : null}
      {/* 框内一行：文字弹性 钮贴底不跟着长 排布见 globals.css */}
      {/* 键位提示不常驻：Enter 发送试一次就会 不值框里一行 想找的人在钮上悬停能看到 */}
      <div className="agent-composer-box">
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0];
            event.currentTarget.value = "";
            if (!file) return;
            if (!file.type.startsWith("image/")) {
              toast.error("请选择图片文件");
              return;
            }
            if (file.size > 8 * 1024 * 1024) {
              toast.error("图片不能超过 8MB");
              return;
            }
            if (image) URL.revokeObjectURL(image.preview);
            setImage({ file, preview: URL.createObjectURL(file) });
          }}
        />
        <button
          type="button"
          className="agent-composer-attach-btn"
          aria-label="上传现场图片"
          title="上传图片"
          disabled={isStreaming || awaiting || uploading}
          onClick={() => fileRef.current?.click()}
        >
          <ImagePlus className="h-4 w-4" />
        </button>
        <textarea
          ref={textareaRef}
          className="agent-composer-input"
          value={value}
          rows={1}
          placeholder={awaiting ? "请先在上方确认或取消该操作" : "给 SafeGuard Agent 发送消息"}
          onChange={(event) => setValue(event.target.value)}
          onCompositionStart={() => {
            isComposingRef.current = true;
          }}
          onCompositionEnd={() => {
            isComposingRef.current = false;
          }}
          onKeyDown={(event) => {
            if (event.key === "Enter" && !event.shiftKey) {
              const nativeEvent = event.nativeEvent as KeyboardEvent;
              if (nativeEvent.isComposing || isComposingRef.current || nativeEvent.keyCode === 229) {
                return;
              }
              event.preventDefault();
              void submit();
            }
          }}
          aria-label="消息"
        />
        {/* 两态共用一枚圆钮：图标换 位置与尺寸不动 换态时框内不跳
            字换成图形后标签只剩无障碍名 title 顺带把上一轮删掉的键位提示接回来（只在悬停时占位） */}
        {isStreaming ? (
          <button
            type="button"
            className="agent-composer-btn"
            data-stop="true"
            aria-label="停止生成"
            title="停止生成"
            onClick={() => {
              cancelGeneration();
              focusInput();
            }}
          >
            <Square className="h-3 w-3" fill="currentColor" strokeWidth={0} />
          </button>
        ) : (
          <button
            type="button"
            className="agent-composer-btn"
            aria-label="发送"
            title="发送（Enter）"
            onClick={() => void submit()}
            disabled={(value.trim() === "" && !image) || awaiting || uploading}
          >
            {uploading ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowUp className="h-4 w-4" strokeWidth={2.5} />}
          </button>
        )}
      </div>
      {/* 免责一行在框外：框里不摆第二行是因为没有真控件 这句有真职责 */}
      <p className="agent-composer-note">内容由 AI 生成，请仔细甄别</p>
    </div>
  );
}
