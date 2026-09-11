package com.safeguard.agent.core.parser.model;

/**
 * 资产引用：指向对象存储中已上传的二进制资源（图片等），由解析器上传后构造，挂在 ImageBlock 上随块元数据落地
 *
 * @param publicUrl 浏览器可直连的公开预览 URL，形如 <a href="http://localhost:9000/safeguard-assets/xxx.png">...</a>（资产桶已开公共读）
 */
public record AssetRef(String publicUrl, String mime) {
}
