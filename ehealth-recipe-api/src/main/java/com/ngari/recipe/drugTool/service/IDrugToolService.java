package com.ngari.recipe.drugTool.service;

import ctd.util.annotation.RpcService;

import java.util.Map;

/**
 * created by shiyuping on 2019/2/14
 */
public interface IDrugToolService {

    @RpcService
    Map<String, Object> readDrugExcel(byte[] buf, String originalFilename, int organId, String operator);
}
