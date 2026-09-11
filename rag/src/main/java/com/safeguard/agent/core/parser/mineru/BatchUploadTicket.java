package com.safeguard.agent.core.parser.mineru;

/**
 * MinerU 申请上传链接接口的返回凭证(单文件)
 * <p>
 * {@link MinerUClient#requestUpload} 返回:batchId 用于后续轮询,
 * uploadUrl 是 MinerU OSS 的预签名 PUT 链接(24h 有效),把文件字节直接 PUT 上去即可
 *
 * @param batchId   MinerU 分配的 batch_id,轮询/下载凭据
 * @param uploadUrl 文件上传目标 URL,PUT 原始字节,无须鉴权头
 */
public record BatchUploadTicket(
        String batchId,
        String uploadUrl
) {
}
