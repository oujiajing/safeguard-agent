package com.safeguard.agent.legal.dao.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.safeguard.agent.framework.database.JsonbTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "t_legal_quality_report", autoResultMap = true)
public class LegalQualityReportDO {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    private String documentId;
    private Integer pageCount;
    private Integer tableCount;
    private Integer parsedTextLength;
    private Integer chapterCount;
    private Integer sectionCount;
    private Integer clauseCount;
    private Integer normativeClauseCount;
    private Integer commentaryClauseCount;
    private Integer supplementaryCount;
    private Integer appendixCount;
    private Integer unknownRoleCount;
    private Integer unstructuredParagraphCount;
    private Integer duplicateClauseCount;
    private Integer chunkCount;
    private Integer oversizedChunkCount;
    private Integer emptyChunkCount;
    private String qualityStatus;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String warnings;
    private Date createTime;
}
