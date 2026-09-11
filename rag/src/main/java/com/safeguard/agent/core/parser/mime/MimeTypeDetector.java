package com.safeguard.agent.core.parser.mime;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.tika.Tika;

/**
 * MIME 探测器：字节语义的唯一权威源，产出只服务解析路由，不参与展示
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MimeTypeDetector {

    private static final Tika TIKA = new Tika();

    /**
     * 按字节 + 文件名探测 MIME，文件名可为空，字节为空返回 null
     */
    public static String detect(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        if (fileName == null) {
            return TIKA.detect(bytes);
        }
        return TIKA.detect(bytes, fileName);
    }
}
