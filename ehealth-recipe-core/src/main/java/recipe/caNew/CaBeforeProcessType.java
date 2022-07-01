package recipe.caNew;

import com.alibaba.fastjson.JSON;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.caNew.pdf.CreatePdfFactory;
import recipe.dao.RecipeDAO;
import recipe.enumerate.status.RecipeStateEnum;
import recipe.enumerate.status.RecipeStatusEnum;
import recipe.enumerate.status.SignEnum;

import java.util.List;

import static ctd.persistence.DAOFactory.getDAO;

//JRK
//前置处方签名实现
public class CaBeforeProcessType extends AbstractCaProcessType {
    private static final Logger LOGGER = LoggerFactory.getLogger(CaBeforeProcessType.class);
    //我们将开方的流程拆开：
    //前置CA操作：1.保存处方（公共操作）=》2.触发CA结果=》3.成功后将处方推送到his，推送相关操作

    @Override
    public void signCABeforeRecipeFunction(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList) {
        LOGGER.info("Before---signCABeforeRecipeFunction 当前CA执行签名之前特应性行为，入参：recipeBean：{}，detailBeanList：{} ", JSONUtils.toString(recipeBean), JSONUtils.toString(detailBeanList));
        //设置处方状态为：签名中
        stateManager.updateStatus(recipeBean.getRecipeId(), RecipeStatusEnum.RECIPE_STATUS_SIGN_ING_CODE_DOC, SignEnum.sign_STATE_SUBMIT);
        stateManager.updateRecipeState(recipeBean.getRecipeId(), RecipeStateEnum.PROCESS_STATE_SUBMIT, RecipeStateEnum.NONE);
    }

    @Override
    public void signCAAfterRecipeCallBackFunction(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList) {
        LOGGER.info("Before---signCAAfterRecipeCallBackFunction 当前CA执行签名之后回调特应性行为，入参：recipeBean：{}，detailBeanList：{} ", JSONUtils.toString(recipeBean), JSONUtils.toString(detailBeanList));
        try {
            recipeHisResultBeforeCAFunction(recipeBean, detailBeanList);
        } catch (Exception e) {
            LOGGER.error("CaBeforeProcessType signCAAfterRecipeCallBackFunction recipeBean= {}", JSON.toJSONString(recipeBean), e);
        }
    }

    @Override
    public RecipeResultBean hisCallBackCARecipeFunction(Integer recipeId) {
        LOGGER.info("Before---当前CA执行his回调之后组装CA响应特应性行为，入参：recipeId：{}", recipeId);
        //这里前置，触发CA时机在推送之前,这里判定his之后的为签名成功
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        List<String> recipeTypes = configurationClient.getValueListCatch(recipe.getClinicOrgan(), "patientRecipeUploadHis", null);
        if (CollectionUtils.isEmpty(recipeTypes)) {
            CreatePdfFactory createPdfFactory = AppContextHolder.getBean("createPdfFactory", CreatePdfFactory.class);
            createPdfFactory.updateCodePdfExecute(recipeId);
        }
        //将返回的CA结果给处方，设置处方流转
        RecipeResultBean recipeResultBean = new RecipeResultBean();
        recipeResultBean.setCode(RecipeResultBean.SUCCESS);
        return recipeResultBean;
    }
}