package com.safeguard.agent.admin.service;

import com.safeguard.agent.admin.controller.vo.DashboardOverviewVO;
import com.safeguard.agent.admin.controller.vo.DashboardPerformanceVO;
import com.safeguard.agent.admin.controller.vo.DashboardTrendsVO;

public interface DashboardService {

    DashboardOverviewVO loadOverview(String window);

    DashboardPerformanceVO loadPerformance(String window);

    DashboardTrendsVO loadTrends(String metric, String window, String granularity);
}
