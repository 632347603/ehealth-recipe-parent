package recipe.thread;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import ctd.util.AppContextHolder;
import eh.recipeaudit.api.IAuditMedicinesService;
import eh.recipeaudit.model.AuditMedicineIssueBean;
import eh.recipeaudit.model.AuditMedicinesBean;
import eh.recipeaudit.model.Intelligent.AutoAuditResultBean;
import eh.recipeaudit.model.Intelligent.IssueBean;
import eh.recipeaudit.model.Intelligent.PAWebMedicinesBean;
import eh.recipeaudit.model.Intelligent.PAWebRecipeDangerBean;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ApplicationUtils;
import recipe.audit.service.PrescriptionService;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author： 0184/yu_yun
 * @date： 2018/11/27
 * @description： 保存审方信息
 * @version： 1.0
 */
public class SaveAutoReviewRunable implements Runnable {

    /**
     * logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SaveAutoReviewRunable.class);

    private RecipeBean recipe;

    private List<RecipeDetailBean> details;

    public SaveAutoReviewRunable(RecipeBean recipe, List<RecipeDetailBean> details) {
        this.recipe = recipe;
        this.details = details;
    }

    @Override
    public void run() {
        LOGGER.info("SaveAutoReviewRunnable start. recipe={}", JSON.toJSONString(recipe));
        try{
            Integer recipeId = recipe.getRecipeId();
            PrescriptionService prescriptionService = ApplicationUtils.getRecipeService(PrescriptionService.class);
            IAuditMedicinesService iAuditMedicinesService = AppContextHolder.getBean("recipeaudit.remoteAuditMedicinesService", IAuditMedicinesService.class);
            AutoAuditResultBean autoAuditResult = prescriptionService.analysis(recipe, details);
            List<AuditMedicinesBean> auditMedicinesList = Lists.newArrayList();
            List<PAWebMedicinesBean> paResultList = autoAuditResult.getMedicines();
            List<PAWebRecipeDangerBean> recipeDangers = autoAuditResult.getRecipeDangers();
            if (CollectionUtils.isNotEmpty(recipeDangers)) {
                recipeDangers.forEach(item -> {
                    AuditMedicineIssueBean auditMedicineIssue = new AuditMedicineIssueBean();
                    auditMedicineIssue.setRecipeId(recipeId);
                    auditMedicineIssue.setLvl(item.getDangerType());
                    auditMedicineIssue.setLvlCode(item.getDangerLevel());
                    auditMedicineIssue.setDetail(item.getDangerDesc());
                    auditMedicineIssue.setTitle(item.getDangerDrug());
                    auditMedicineIssue.setCreateTime(new Date());
                    auditMedicineIssue.setLastModify(new Date());
                    auditMedicineIssue.setDetailUrl(item.getDetailUrl());
                    auditMedicineIssue.setLogicalDeleted(0);
                    iAuditMedicinesService.saveAuditMedicineIssue(auditMedicineIssue);
                });
            }
            if (CollectionUtils.isEmpty(paResultList)) {
                AuditMedicinesBean auditMedicinesDTO = new AuditMedicinesBean();
                auditMedicinesDTO.setRecipeId(recipeId);
                auditMedicinesDTO.setRemark(autoAuditResult.getMsg());
                auditMedicinesList.add(auditMedicinesDTO);
                iAuditMedicinesService.saveAuditMedicines(recipeId, auditMedicinesList);
            } else if (CollectionUtils.isNotEmpty(paResultList)) {
                AuditMedicinesBean auditMedicinesDTO;
                List<IssueBean> issueList;
                List<AuditMedicineIssueBean> auditMedicineIssues;
                AuditMedicineIssueBean auditIssueDTO;
                for (PAWebMedicinesBean paMedicine : paResultList) {
                    auditMedicinesDTO = new AuditMedicinesBean();
                    auditMedicinesDTO.setRecipeId(recipeId);
                    auditMedicinesDTO.setCode(paMedicine.getCode());
                    auditMedicinesDTO.setName(paMedicine.getName());
                    issueList = paMedicine.getIssues();
                    if (CollectionUtils.isNotEmpty(issueList)) {
                        auditMedicineIssues = new ArrayList<>(issueList.size());
                        for (IssueBean issue : issueList) {
                            auditIssueDTO = new AuditMedicineIssueBean();
                            auditIssueDTO.setDetail(issue.getDetail());
                            auditIssueDTO.setLvl(issue.getLvl());
                            auditIssueDTO.setLvlCode(issue.getLvlCode());
                            auditIssueDTO.setTitle(issue.getTitle());
                            auditMedicineIssues.add(auditIssueDTO);
                        }
                        auditMedicinesDTO.setAuditMedicineIssues(auditMedicineIssues);
                    }
                    auditMedicinesList.add(auditMedicinesDTO);
                }
                iAuditMedicinesService.saveAuditMedicines(recipeId, auditMedicinesList);
            }
        }catch (Exception e){
            LOGGER.error("SaveAutoReviewRunnable error,recipe={}", JSON.toJSONString(recipe), e);
        }
        LOGGER.info("SaveAutoReviewRunnable finish. recipe={}", JSON.toJSONString(recipe));
    }

}
