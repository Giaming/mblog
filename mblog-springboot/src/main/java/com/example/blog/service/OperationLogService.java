package com.example.blog.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.blog.dto.OperationLogDTO;
import com.example.blog.entity.OperationLog;
import com.example.blog.vo.PageResult;
import com.example.blog.vo.ConditionVO;

/**
 * 操作日志服务
 */
public interface OperationLogService extends IService<OperationLog> {

    /**
     * 查询日志列表
     *
     * @param conditionVO 条件
     * @return 日志列表
     */
    PageResult<OperationLogDTO> listOperationLogs(ConditionVO conditionVO);

}
