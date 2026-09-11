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
@TableName(value = "t_legal_clause", autoResultMap = true)
public class LegalClauseDO {

    @TableId(type = IdType.INPUT)
    private String id;
    private String documentId;
    private String contentRole;
    private String structureType;
    private String chapterNo;
    private String chapterTitle;
    private String sectionNo;
    private String sectionTitle;
    private String clauseNo;
    private String hierarchyPath;
    private String rawText;
    private String normalizedText;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String childrenJson;
    private String firstElementId;
    private String lastElementId;
    private Integer pageStart;
    private Integer pageEnd;
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String provenance;
    private Boolean indexEligible;
    private String duplicateOfClauseId;
    private Date createTime;
}
