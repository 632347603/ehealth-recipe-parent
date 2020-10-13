package recipe.caNew;

import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import ctd.util.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.RecipeDAO;

import java.util.List;

import static ctd.persistence.DAOFactory.getDAO;

//JRK
//前置处方签名实现
@Service("caBeforeProcessType")
public class CaBeforeProcessType extends AbstractCaProcessType{
    private static final Logger LOGGER = LoggerFactory.getLogger(CaBeforeProcessType.class);

    //我们将开方的流程拆开：
    //前置CA操作：1.保存处方（公共操作）=》2.触发CA结果=》3.成功后将处方推送到his，推送相关操作

    @Override
    public void signCABeforeRecipeFunction(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList){
        LOGGER.info("Before---signCABeforeRecipeFunction 当前CA执行签名之前特应性行为，入参：recipeBean：{}，detailBeanList：{} ",  JSONUtils.toString(recipeBean),  JSONUtils.toString(detailBeanList));
        //前置签名，CA前操作，将处方设置成【医生签名中】
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Integer signRecipeStatus = RecipeStatusConstant.SIGN_ING_CODE_DOC;
        recipeDAO.updateRecipeInfoByRecipeId(recipeBean.getRecipeId(), signRecipeStatus, null);
    }

    @Override
    public void signCAAfterRecipeCallBackFunction(RecipeBean recipeBean, List<RecipeDetailBean> detailBeanList){
        LOGGER.info("Before---signCAAfterRecipeCallBackFunction 当前CA执行签名之后回调特应性行为，入参：recipeBean：{}，detailBeanList：{} ",  JSONUtils.toString(recipeBean),  JSONUtils.toString(detailBeanList));
        recipeHisResultBeforeCAFunction(recipeBean,detailBeanList);
    }

    @Override
    public RecipeResultBean hisCallBackCARecipeFunction(Integer recipeId) {
        LOGGER.info("Before---当前CA执行his回调之后组装CA响应特应性行为，入参：recipeId：{}", recipeId);
        //这里前置，触发CA时机在推送之前,这里判定his之后的为签名成功
        RecipeResultBean recipeResultBean = new RecipeResultBean();
        recipeResultBean.setCode(RecipeResultBean.SUCCESS);
        //将返回的CA结果给处方，设置处方流转
        return recipeResultBean;
    }

}