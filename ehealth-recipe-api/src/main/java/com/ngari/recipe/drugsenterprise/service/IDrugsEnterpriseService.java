package com.ngari.recipe.drugsenterprise.service;

import com.ngari.recipe.IBaseService;
import com.ngari.recipe.drugsenterprise.model.DrugsEnterpriseBean;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.util.annotation.RpcService;

import java.util.List;

/**
 * @company: ngarihealth
 * @author: 0184/yu_yun
 * @date:2017/9/11.
 */
public interface IDrugsEnterpriseService extends IBaseService<DrugsEnterpriseBean> {
    /**
     * 推送处方信息
     * @param recipe
     * @param drugsEnterprise
     */
    @RpcService
    void pushRecipeInfoForThird(RecipeBean recipe,DrugsEnterpriseBean drugsEnterprise);


    @RpcService(mvcDisabled = false)
    DrugsEnterpriseBean getByEnterpriseCode(Integer enterId);

    /**
     * 导出批量查询药企信息
     * @param enterIds
     * @return
     */
    @RpcService(mvcDisabled = false)
    List<DrugsEnterpriseBean> findByEnterpriseIdList(List<Integer> enterIds);

}
