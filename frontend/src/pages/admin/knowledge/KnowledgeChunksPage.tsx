import { useCallback, useEffect, useMemo, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { CircleHelp, PenSquare, Plus, RefreshCw, ShieldCheck, ShieldX, Trash2 } from "lucide-react";
import { toast } from "sonner";

import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "@/components/ui/alert-dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from "@/components/ui/tooltip";
import { RelativeTime } from "@/components/RelativeTime";
import { PdfPreview } from "@/components/document/PdfPreview";

import type { KnowledgeChunk, KnowledgeDocument, LegalChunkSourceContext, LegalReviewSignal, PageResult } from "@/services/knowledgeService";
import {
  batchToggleChunks,
  createChunk,
  deleteChunk,
  toggleChunk,
  getChunksPage,
  getDocument,
  getKnowledgeBase,
  updateChunk,
  getLegalReviewSignals,
  reviewLegalSignal,
  getChunkChapters,
  getLegalChunkSourceContext
} from "@/services/knowledgeService";
import { getErrorMessage } from "@/utils/error";

const PAGE_SIZE = 10;

const truncateText = (value?: string | null, max = 120) => {
  if (!value) return "-";
  if (value.length <= max) return value;
  return `${value.slice(0, max)}...`;
};

const enabledLabel = (enabled?: number | null) => (enabled === 1 ? "启用" : "禁用");

export function KnowledgeChunksPage() {
  const { kbId, docId } = useParams();
  const navigate = useNavigate();
  const [doc, setDoc] = useState<KnowledgeDocument | null>(null);
  const [kbName, setKbName] = useState("");
  const [pageData, setPageData] = useState<PageResult<KnowledgeChunk> | null>(null);
  const [pageNo, setPageNo] = useState(1);
  const [loading, setLoading] = useState(false);
  const [enabledFilter, setEnabledFilter] = useState<number | undefined>();
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [createOpen, setCreateOpen] = useState(false);
  const [editDialog, setEditDialog] = useState<{ open: boolean; chunk: KnowledgeChunk | null }>({
    open: false,
    chunk: null
  });
  const [deleteTarget, setDeleteTarget] = useState<KnowledgeChunk | null>(null);
  const [chapters, setChapters] = useState<string[]>([]);
  const [chapterFilter, setChapterFilter] = useState<string>("all");
  const [reviewSignals, setReviewSignals] = useState<LegalReviewSignal[]>([]);
  const [reviewFilter, setReviewFilter] = useState<string>("all");
  const [reviewOpen, setReviewOpen] = useState<LegalReviewSignal | null>(null);
  const [reviewReason, setReviewReason] = useState("");
  const [pageInput, setPageInput] = useState("");
  const chunks = pageData?.records || [];

  const selectedList = useMemo(() => Array.from(selectedIds), [selectedIds]);

  const loadDocument = useCallback(async () => {
    if (!docId) return;
    try {
      const data = await getDocument(docId);
      setDoc(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载文档失败"));
      console.error(error);
    }
  }, [docId]);

  const loadChunks = useCallback(async (current = pageNo, enabled = enabledFilter, chapter = chapterFilter, review = reviewFilter) => {
    if (!docId) return;
    setLoading(true);
    try {
      const data = await getChunksPage(docId, {
        current,
        size: PAGE_SIZE,
        enabled,
        chapterNo: chapter === "all" ? undefined : chapter,
        reviewStatus: review === "all" ? undefined : review
      });
      setPageData(data);
    } catch (error) {
      toast.error(getErrorMessage(error, "加载分块失败"));
      console.error(error);
    } finally {
      setLoading(false);
    }
  }, [docId, enabledFilter, pageNo, chapterFilter, reviewFilter]);

  useEffect(() => {
    loadDocument();
  }, [loadDocument]);

  useEffect(() => {
    if (kbId) {
      getKnowledgeBase(kbId).then(kb => setKbName(kb.name)).catch(() => {});
    }
  }, [kbId]);

  useEffect(() => {
    if (docId && doc?.processingStrategy === "LEGAL") {
      getChunkChapters(docId).then(setChapters).catch(() => setChapters([]));
    } else {
      setChapters([]);
    }
  }, [docId, doc?.processingStrategy]);

  useEffect(() => {
    loadChunks();
  }, [loadChunks]);

  const loadLegalReview = useCallback(async () => {
    if (!docId || doc?.processingStrategy !== "LEGAL") return;
    try {
      setReviewSignals(await getLegalReviewSignals(docId));
    } catch (error) {
      toast.error(getErrorMessage(error, "加载法律复核信息失败"));
    }
  }, [docId, doc?.processingStrategy]);

  useEffect(() => {
    loadLegalReview();
  }, [loadLegalReview]);

  useEffect(() => {
    setSelectedIds(new Set());
  }, [docId, enabledFilter, chapterFilter, reviewFilter]);

  const signalsByChunk = useMemo(() => {
    const result = new Map<string, LegalReviewSignal[]>();
    reviewSignals.forEach((signal) => signal.relatedChunkIds.forEach((chunkId) => {
      const current = result.get(String(chunkId)) || [];
      current.push(signal);
      result.set(String(chunkId), current);
    }));
    return result;
  }, [reviewSignals]);

  const jumpToPage = () => {
    const target = Number(pageInput);
    const maxPage = pageData?.pages || 0;
    if (!Number.isInteger(target) || target < 1 || target > maxPage) {
      toast.error(maxPage > 0 ? `请输入 1-${maxPage} 之间的页码` : "当前没有可跳转的页面");
      return;
    }
    setPageNo(target);
    setPageInput("");
  };

  const allSelected = chunks.length > 0 && chunks.every((chunk) => selectedIds.has(String(chunk.id)));

  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedIds(new Set());
      return;
    }
    const next = new Set(selectedIds);
    chunks.forEach((chunk) => next.add(String(chunk.id)));
    setSelectedIds(next);
  };

  const toggleSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleBatchToggle = async (enabled: boolean) => {
    if (!docId) return;
    if (selectedList.length === 0) {
      toast.error("请选择需要操作的分块");
      return;
    }
    const targetValue = enabled ? 1 : 0;
    const selectedChunks = chunks.filter((c) => selectedList.includes(String(c.id)));
    const needChange = selectedChunks.some((c) => c.enabled !== targetValue);
    if (!needChange) {
      toast.info(enabled ? "所选分块已全部启用" : "所选分块已全部禁用");
      return;
    }
    try {
      await batchToggleChunks(docId, enabled, selectedList);
      toast.success(enabled ? "批量启用成功" : "批量禁用成功");
      setSelectedIds(new Set());
      await loadChunks(pageNo, enabledFilter);
    } catch (error) {
      toast.error(getErrorMessage(error, enabled ? "批量启用失败" : "批量禁用失败"));
      console.error(error);
    }
  };

  const handleDelete = async () => {
    if (!docId || !deleteTarget) return;
    try {
      await deleteChunk(docId, String(deleteTarget.id));
      toast.success("删除成功");
      setDeleteTarget(null);
      await loadChunks(pageNo, enabledFilter);
    } catch (error) {
      toast.error(getErrorMessage(error, "删除失败"));
      console.error(error);
    }
  };

  const handleToggleEnabled = async (chunk: KnowledgeChunk) => {
    if (!docId) return;
    try {
      const enable = chunk.enabled !== 1;
      await toggleChunk(docId, String(chunk.id), enable);
      toast.success(enable ? "已启用" : "已禁用");
      await loadChunks(pageNo, enabledFilter);
    } catch (error) {
      toast.error(getErrorMessage(error, "操作失败"));
      console.error(error);
    }
  };

  return (
    <div className="admin-page">
      <div className="admin-page-header">
        <div>
          <h1 className="admin-page-title">分块管理</h1>
          <p className="admin-page-subtitle">
            {doc?.docName || docId} {kbName ? `（知识库: ${kbName}）` : ""}
          </p>
        </div>
        <div className="admin-page-actions">
          <Button variant="outline" onClick={() => navigate(-1)}>
            返回文档
          </Button>
          <Button className="admin-primary-gradient" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            新建分块
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle>Chunk 列表</CardTitle>
              <CardDescription>支持编辑、启停、批量操作</CardDescription>
            </div>
            <div className="flex flex-1 flex-wrap items-center justify-end gap-2">
              {doc?.processingStrategy === "LEGAL" ? (
                <>
                  <Select value={chapterFilter} onValueChange={(value) => { setPageNo(1); setChapterFilter(value); }}>
                    <SelectTrigger className="w-[160px]"><SelectValue placeholder="按章筛选" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">全部章节</SelectItem>
                      {chapters.map((chapter) => <SelectItem key={chapter || "uncategorized"} value={chapter || "__UNCATEGORIZED__"}>{chapter || "未归类"}</SelectItem>)}
                    </SelectContent>
                  </Select>
                  <Select value={reviewFilter} onValueChange={(value) => { setPageNo(1); setReviewFilter(value); }}>
                    <SelectTrigger className="w-[160px]"><SelectValue placeholder="复核状态" /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">全部复核状态</SelectItem>
                      <SelectItem value="NEEDS_REVIEW">需要复核</SelectItem>
                      <SelectItem value="ISSUE_CONFIRMED">已确认异常</SelectItem>
                      <SelectItem value="VERIFIED_OK">已确认正常</SelectItem>
                      <SelectItem value="NOT_FOUND">未发现异常</SelectItem>
                      <SelectItem value="NOT_DETECTED">未检测</SelectItem>
                      <SelectItem value="DETECTION_FAILED">检测失败</SelectItem>
                    </SelectContent>
                  </Select>
                </>
              ) : null}
              <Select
                value={enabledFilter === undefined ? "all" : String(enabledFilter)}
                onValueChange={(value) => {
                  setPageNo(1);
                  setEnabledFilter(value === "all" ? undefined : Number(value));
                }}
              >
                <SelectTrigger className="w-[160px]">
                  <SelectValue placeholder="启用状态" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">全部状态</SelectItem>
                  <SelectItem value="1">启用</SelectItem>
                  <SelectItem value="0">禁用</SelectItem>
                </SelectContent>
              </Select>
              <Button
                variant="outline"
                onClick={() => {
                  setPageNo(1);
                  loadChunks(1, enabledFilter);
                }}
              >
                <RefreshCw className="mr-2 h-4 w-4" />
                刷新
              </Button>
              <Button variant="outline" onClick={() => handleBatchToggle(true)} disabled={selectedList.length === 0}>
                <ShieldCheck className="mr-2 h-4 w-4" />
                批量启用
              </Button>
              <Button variant="outline" onClick={() => handleBatchToggle(false)} disabled={selectedList.length === 0}>
                <ShieldX className="mr-2 h-4 w-4" />
                批量禁用
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          {loading ? (
            <div className="py-8 text-center text-muted-foreground">加载中...</div>
          ) : chunks.length === 0 ? (
            <div className="py-8 text-center text-muted-foreground">暂无分块</div>
          ) : (
            <Table className="min-w-[960px]">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[48px]">
                    <input type="checkbox" checked={allSelected} onChange={toggleSelectAll} />
                  </TableHead>
                  <TableHead className="w-[70px]">序号</TableHead>
                  <TableHead>内容</TableHead>
                  <TableHead className="w-[150px]">状态</TableHead>
                  <TableHead className="w-[90px]">字符数</TableHead>
                  <TableHead className="w-[90px]">
                    <span className="inline-flex items-center gap-1">
                      Token
                      <TooltipProvider delayDuration={0}>
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <CircleHelp className="h-3.5 w-3.5 text-muted-foreground" />
                          </TooltipTrigger>
                          <TooltipContent side="top">
                            <span className="text-xs font-normal">预估Token数，仅提供参考</span>
                          </TooltipContent>
                        </Tooltip>
                      </TooltipProvider>
                    </span>
                  </TableHead>
                  <TableHead className="w-[170px]">更新时间</TableHead>
                  <TableHead className="w-[140px] text-left">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {chunks.map((chunk) => (
                  <TableRow key={chunk.id}>
                    <TableCell>
                      <input
                        type="checkbox"
                        checked={selectedIds.has(String(chunk.id))}
                        onChange={() => toggleSelect(String(chunk.id))}
                      />
                    </TableCell>
                    <TableCell>{chunk.chunkIndex ?? "-"}</TableCell>
                    <TableCell className="max-w-[360px] text-sm text-muted-foreground break-all">
                      {truncateText(chunk.content)}
                    </TableCell>
                    <TableCell>
                      <div className="flex flex-col items-start gap-1">
                        <Badge variant={chunk.enabled === 1 ? "default" : "outline"}>{enabledLabel(chunk.enabled)}</Badge>
                        {doc?.processingStrategy === "LEGAL" && chunk.reviewStatus && chunk.reviewStatus !== "NOT_FOUND" ? (
                          <Badge
                            variant={chunk.reviewStatus === "NEEDS_REVIEW" ? "destructive" : "secondary"}
                            className="cursor-pointer"
                            onClick={() => {
                              const signal = signalsByChunk.get(String(chunk.id))?.[0];
                              if (signal) { setReviewReason(""); setReviewOpen(signal); }
                            }}
                          >
                            {reviewStatusLabel(chunk.reviewStatus, chunk.reviewIssueCount)}
                          </Badge>
                        ) : null}
                      </div>
                    </TableCell>
                    <TableCell>{chunk.charCount ?? "-"}</TableCell>
                    <TableCell>{chunk.tokenCount ?? "-"}</TableCell>
                    <TableCell><RelativeTime value={chunk.updateTime} /></TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-2">
                        <Button size="sm" variant="outline" onClick={() => setEditDialog({ open: true, chunk })}>
                          <PenSquare className="mr-0.1 h-4 w-4" />
                          编辑
                        </Button>
                        <Button size="sm" variant="outline" onClick={() => handleToggleEnabled(chunk)}>
                          {chunk.enabled === 1 ? "禁用" : "启用"}
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-destructive hover:text-destructive"
                          onClick={() => setDeleteTarget(chunk)}
                        >
                          <Trash2 className="mr-0.1 h-4 w-4" />
                          删除
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}

          {pageData ? (
            <div className="mt-4 flex flex-wrap items-center justify-between gap-2 text-sm text-slate-500">
              <span>共 {pageData.total} 条</span>
              <div className="flex items-center gap-2">
                <Button variant="outline" size="sm" onClick={() => setPageNo((prev) => Math.max(1, prev - 1))} disabled={pageData.current <= 1}>
                  上一页
                </Button>
                <span>
                  {pageData.current} / {pageData.pages}
                </span>
                <Input
                  className="h-8 w-20"
                  value={pageInput}
                  placeholder="页码"
                  onChange={(event) => setPageInput(event.target.value)}
                  onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); jumpToPage(); } }}
                />
                <Button variant="outline" size="sm" onClick={jumpToPage}>跳转</Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setPageNo((prev) => Math.min(pageData.pages || 1, prev + 1))}
                  disabled={pageData.current >= pageData.pages}
                >
                  下一页
                </Button>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Dialog open={Boolean(reviewOpen)} onOpenChange={(open) => !open && setReviewOpen(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>问题复核</DialogTitle>
          </DialogHeader>
          <div className="space-y-2 text-sm text-muted-foreground">
            <div><span className="font-medium text-foreground">可疑问题：</span>{reviewOpen?.message || "-"}</div>
            <div><span className="font-medium text-foreground">关联条款：</span>{reviewOpen?.relatedClauseNos.join("、") || "当前条款"}</div>
          </div>
          {reviewOpen?.reviewStatus === "PENDING_REVIEW" ? <Textarea value={reviewReason} onChange={(event) => setReviewReason(event.target.value)} placeholder="请输入核对依据" /> : null}
          <DialogFooter>
            <Button variant="outline" onClick={() => setReviewOpen(null)}>关闭</Button>
            {reviewOpen?.reviewStatus === "PENDING_REVIEW" ? <>
              <Button variant="outline" onClick={async () => {
                if (!reviewOpen || !reviewReason.trim()) return toast.error("请输入复核原因");
                await reviewLegalSignal(reviewOpen.id, { signalStatus: "VERIFIED_OK", reason: reviewReason.trim(), expectedVersion: reviewOpen.version });
                setReviewOpen(null);
                await loadLegalReview();
                await loadChunks(pageNo, enabledFilter);
              }}>确认正常</Button>
              <Button variant="destructive" onClick={async () => {
                if (!reviewOpen || !reviewReason.trim()) return toast.error("请输入复核原因");
                await reviewLegalSignal(reviewOpen.id, { signalStatus: "ISSUE_CONFIRMED", reason: reviewReason.trim(), expectedVersion: reviewOpen.version });
                setReviewOpen(null);
                await loadLegalReview();
                await loadChunks(pageNo, enabledFilter);
              }}>确认异常</Button>
            </> : <span className="self-center text-sm">已记录：{reviewOpen?.reviewReason || "-"}</span>}
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ChunkDialog
        mode="create"
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSubmit={async (payload) => {
          if (!docId) return;
          await createChunk(docId, { content: payload.content, index: payload.index });
          toast.success("创建成功");
          setCreateOpen(false);
          await loadChunks(pageNo, enabledFilter);
        }}
      />

      <ChunkDialog
        mode="edit"
        open={editDialog.open}
        chunk={editDialog.chunk}
        legal={doc?.processingStrategy === "LEGAL"}
        onOpenChange={(open) => setEditDialog({ open, chunk: open ? editDialog.chunk : null })}
        onSubmit={async (payload) => {
          if (!docId || !editDialog.chunk) return;
          await updateChunk(docId, String(editDialog.chunk.id), { content: payload.content });
          toast.success("更新成功");
          setEditDialog({ open: false, chunk: null });
          await loadChunks(pageNo, enabledFilter);
        }}
      />

      <AlertDialog open={Boolean(deleteTarget)} onOpenChange={(open) => (!open ? setDeleteTarget(null) : null)}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>确认删除分块？</AlertDialogTitle>
            <AlertDialogDescription>该分块将被删除且向量会清理。</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>取消</AlertDialogCancel>
            <AlertDialogAction onClick={handleDelete} className="bg-destructive text-destructive-foreground">
              删除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

    </div>
  );
}

const reviewStatusLabel = (status?: KnowledgeChunk["reviewStatus"], count?: number | null) => {
  if (status === "NEEDS_REVIEW") return `需要复核${count ? ` (${count})` : ""}`;
  if (status === "ISSUE_CONFIRMED") return "已确认异常";
  if (status === "VERIFIED_OK") return "已确认正常";
  if (status === "DETECTION_FAILED") return "检测失败";
  if (status === "DETECTION_PENDING") return "检测中";
  if (status === "NOT_DETECTED") return "未检测";
  if (status === "NOT_FOUND") return "未发现异常";
  return "";
};

interface ChunkDialogProps {
  mode: "create" | "edit";
  open: boolean;
  chunk?: KnowledgeChunk | null;
  legal?: boolean;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: { content: string; index?: number | null }) => Promise<void>;
}

function ChunkDialog({ mode, open, chunk, legal = false, onOpenChange, onSubmit }: ChunkDialogProps) {
  const [content, setContent] = useState("");
  const [chunkIndex, setChunkIndex] = useState<string>("");
  const [saving, setSaving] = useState(false);
  const [sourceContext, setSourceContext] = useState<LegalChunkSourceContext | null>(null);
  const [pdfPage, setPdfPage] = useState(1);
  const [pdfPageInput, setPdfPageInput] = useState("1");
  const [pdfPageCount, setPdfPageCount] = useState(0);
  const [pdfZoom, setPdfZoom] = useState(1);

  useEffect(() => {
    if (!open) return;
    if (mode === "edit") {
      setContent(chunk?.content || "");
      setChunkIndex("");
      setSourceContext(null);
      setPdfPage(1);
      setPdfPageInput("1");
      setPdfPageCount(0);
      setPdfZoom(1);
      if (legal && chunk?.docId && chunk.id) {
        getLegalChunkSourceContext(String(chunk.docId), String(chunk.id))
          .then(context => {
            setSourceContext(context);
            const initialPage = context.pageStart && context.pageStart > 0 ? context.pageStart : 1;
            setPdfPage(initialPage);
            setPdfPageInput(String(initialPage));
          })
          .catch(() => setSourceContext({ available: false, message: "暂无原文对照" }));
      }
      return;
    }
    setContent("");
    setChunkIndex("");
    setSourceContext(null);
    setPdfPage(1);
    setPdfPageInput("1");
    setPdfPageCount(0);
    setPdfZoom(1);
  }, [open, mode, chunk, legal]);

  const handleSubmit = async () => {
    const trimmed = content.trim();
    if (!trimmed) {
      toast.error("请输入内容");
      return;
    }

    const indexValue = chunkIndex.trim() === "" ? null : Number(chunkIndex);
    if (chunkIndex.trim() !== "" && (Number.isNaN(indexValue) || !Number.isInteger(indexValue) || (indexValue as number) < 0)) {
      toast.error("序号必须为非负整数");
      return;
    }

    setSaving(true);
    try {
      await onSubmit({ content: trimmed, index: indexValue });
    } catch (error) {
      toast.error(getErrorMessage(error, mode === "create" ? "创建失败" : "更新失败"));
      console.error(error);
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="h-[94vh] w-[96vw] max-w-[1500px] overflow-hidden flex flex-col"
        onOpenAutoFocus={(e) => e.preventDefault()}
        onCloseAutoFocus={(e) => { e.preventDefault(); requestAnimationFrame(() => (document.activeElement as HTMLElement)?.blur()); }}
      >
        <DialogHeader>
          <DialogTitle>{mode === "create" ? "新建分块" : "编辑分块"}</DialogTitle>
          <DialogDescription>手动维护分块内容</DialogDescription>
        </DialogHeader>
        <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-hidden px-2 pb-3">
          {mode === "create" && (
            <div className="flex items-baseline gap-3 pt-1">
              <label className="shrink-0 text-sm font-medium">序号</label>
              <Input
                type="number"
                min={0}
                step={1}
                placeholder="0、1..."
                className="[appearance:textfield] [&::-webkit-outer-spin-button]:appearance-none [&::-webkit-inner-spin-button]:appearance-none h-8 w-24"
                value={chunkIndex}
                onChange={(e) => setChunkIndex(e.target.value)}
              />
              <span className="text-xs text-muted-foreground">留空则自动追加到末尾</span>
            </div>
          )}
          <div className={mode === "edit" ? "grid min-h-0 flex-1 gap-4 lg:grid-cols-[minmax(0,3fr)_minmax(360px,2fr)]" : "flex min-h-0 flex-1 flex-col"}>
            {mode === "edit" ? (
              <div className="flex min-h-0 flex-col rounded-md border bg-muted/20 p-3">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <div className="text-sm font-medium">原 PDF 对照</div>
                  <div className="flex items-center gap-1 text-xs">
                    <span>定位页码</span>
                    <Input className="h-7 w-14 px-1 text-center" value={pdfPageInput} onChange={event => setPdfPageInput(event.target.value)} onKeyDown={event => { if (event.key === "Enter") { const next = Math.max(1, Math.min(pdfPageCount || 1, Number(pdfPageInput) || 1)); setPdfPage(next); setPdfPageInput(String(next)); } }} />
                    <span>/ {pdfPageCount || "-"}</span>
                    <Button type="button" variant="outline" size="sm" className="h-7 px-2" onClick={() => { const next = Math.max(1, Math.min(pdfPageCount || 1, Number(pdfPageInput) || 1)); setPdfPage(next); setPdfPageInput(String(next)); }}>定位</Button>
                    <Button type="button" variant="outline" size="sm" className="h-7 px-2" onClick={() => setPdfZoom(value => Math.max(0.75, Number((value - 0.25).toFixed(2))))}>−</Button>
                    <span className="w-10 text-center">{Math.round(pdfZoom * 100)}%</span>
                    <Button type="button" variant="outline" size="sm" className="h-7 px-2" onClick={() => setPdfZoom(value => Math.min(2.5, Number((value + 0.25).toFixed(2))))}>＋</Button>
                  </div>
                </div>
                {legal && sourceContext ? (
                  <>
                    <div className="mb-2 text-xs text-muted-foreground">
                      {sourceContext.available && sourceContext.clauseNo ? `条款 ${sourceContext.clauseNo}` : "暂无可靠条款定位"}
                      {sourceContext.pageStart ? ` · 打开时定位到 PDF 第 ${sourceContext.pageStart} 页` : " · 连续滚动浏览 PDF"}
                    </div>
                    <div className="min-h-0 flex-1 overflow-hidden rounded border bg-white"><PdfPreview docId={String(chunk?.docId)} initialPage={pdfPage} scale={pdfZoom} onPageCount={setPdfPageCount} /></div>
                  </>
                ) : legal ? (
                  <div className="min-h-0 flex-1 overflow-hidden rounded border bg-white"><PdfPreview docId={String(chunk?.docId)} initialPage={pdfPage} scale={pdfZoom} onPageCount={setPdfPageCount} /></div>
                ) : (
                  <div className="text-sm text-muted-foreground">普通文档暂无原文对照</div>
                )}
              </div>
            ) : null}
            <div className="flex min-h-0 flex-1 flex-col">
              <label className="text-sm font-medium">当前分块内容</label>
              <Textarea
                className="mt-2 flex-1 min-h-[280px] resize-none chunk-editor-textarea"
                value={content}
                onChange={(event) => setContent(event.target.value)}
              />
            </div>
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={saving}>
            取消
          </Button>
          <Button onClick={handleSubmit} disabled={saving}>
            {saving ? "保存中..." : "保存"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
