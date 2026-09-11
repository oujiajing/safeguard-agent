package com.safeguard.agent.agent.integration.safeteam;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class SafeTeamContracts {
    private SafeTeamContracts() {}

    public record ApiResponse<T>(int code, T data, Object error, String message) {}
    public record PageResult<T>(List<T> items, long total) {}

    public record OrderQuery(
            String status, Long companyId, Long departmentId, Long teamId,
            Long responsibleUserId, LocalDate dateStart, LocalDate dateEnd,
            Integer page, Integer pageSize) {}

    public record CreateItem(
            String riskType, String checkItem, String hazardDescription,
            String beforePhoto, String beforeVideo, String defaultFollowUpPlan) {}

    public record CreateRequest(
            Long companyId, Long departmentId, Long teamId,
            LocalDate businessDate, List<CreateItem> items,
            String aiSourceHazardId, String aiAssessmentId,
            String aiConfirmationId, String aiIdempotencyKey) {
        public CreateRequest(Long companyId, Long departmentId, Long teamId,
                             LocalDate businessDate, List<CreateItem> items) {
            this(companyId, departmentId, teamId, businessDate, items, null, null, null, null);
        }
    }

    public record ActionRequest(
            String action, Map<String, Object> payload, String remark, Integer version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderListItem(
            String id, String orderNo, String sourceType, String sourceModuleKey,
            String sourceRecordId, String sourceRecordNo, String rootDispatchRecordId,
            String companyId, String company, String departmentId, String department,
            String teamId, String team, String businessDate, Integer hazardCount,
            String status, String statusLabel, String rectificationDeadline,
            String issuedAt, String rectifiedAt, String closedAt, Integer version) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderDetail(
            String id, String orderNo, String sourceType, String sourceModuleKey,
            String sourceRecordId, String sourceRecordNo, String rootDispatchRecordId,
            String companyId, String company, String departmentId, String department,
            String teamId, String team, String businessDate, Integer hazardCount,
            String status, String statusLabel, String rectificationDepartmentId,
            String rectificationResponsibleUserId, String rectificationRequirement,
            String rectificationDeadline, String issuedBy, String issuedAt,
            String rectifiedBy, String rectifiedAt, String rectificationDescription,
            String rectificationAfterPhoto, String acceptanceUserId,
            String acceptanceDepartmentId, String acceptanceAt, String acceptanceResult,
            String acceptanceRemark, String closedAt, Integer version,
            List<OrderItem> items, List<FlowLog> flowLogs) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItem(
            String id, String sourceLineId, Integer sourceLineIndex, String libraryItemId,
            String riskType, String checkItem, String hazardDescription, String aiEnabled,
            String beforePhoto, String beforeVideo, String defaultFollowUpPlan,
            String rectificationStatus, String rectificationStatusLabel, String closedAt,
            Integer sortOrder, Map<String, Object> sourceSnapshot) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FlowLog(
            String id, String fromStatus, String fromStatusLabel, String toStatus,
            String toStatusLabel, String action, String actionLabel, String operatorId,
            String operatorName, String remark, Map<String, Object> payload,
            String createdAt) {}
}
