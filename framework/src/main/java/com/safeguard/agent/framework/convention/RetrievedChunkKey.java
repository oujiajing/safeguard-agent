package com.safeguard.agent.framework.convention;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;

public final class RetrievedChunkKey {

    private RetrievedChunkKey() {
    }

    public static String of(RetrievedChunk chunk) {
        return StrUtil.isNotBlank(chunk.getId())
                ? chunk.getId()
                : DigestUtil.sha256Hex(chunk.getText() == null ? "" : chunk.getText());
    }
}
