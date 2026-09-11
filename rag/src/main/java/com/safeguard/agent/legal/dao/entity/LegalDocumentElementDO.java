package com.safeguard.agent.legal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_legal_document_element")
public class LegalDocumentElementDO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String documentId;
    private Integer elementIndex;
    private String rawText;
    private String normalizedText;
    private String structureType;
    private String contentRole;
    private String canonicalNumber;
    private Integer pageStart;
    private Integer pageEnd;
    private Date createTime;
}
