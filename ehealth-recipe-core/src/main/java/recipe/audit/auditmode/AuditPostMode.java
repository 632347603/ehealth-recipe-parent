package recipe.audit.auditmode;

import com.alibaba.fastjson.JSON;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeOrder;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import eh.cdr.constant.RecipeStatusConstant;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ApplicationUtils;
import recipe.aop.LogRecord;
import recipe.bean.CheckYsInfoBean;
import recipe.constant.CacheConstant;
import recipe.constant.RecipeBussConstant;
import recipe.constant.RecipeMsgEnum;
import recipe.constant.ReviewTypeConstant;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeOrderDAO;
import recipe.drugsenterprise.RemoteDrugEnterpriseService;
import recipe.enumerate.status.RecipeStateEnum;
import recipe.enumerate.status.RecipeStatusEnum;
import recipe.manager.StateManager;
import recipe.service.RecipeLogService;
import recipe.service.RecipeMsgService;
import recipe.service.RecipeService;
import recipe.util.MapValueUtil;
import recipe.util.RedisClient;

import java.util.Map;
import java.util.Set;

import static ctd.persistence.DAOFactory.getDAO;

/**
 * created by shiyuping on 2019/8/15
 * 审方后置
 */
@AuditMode(ReviewTypeConstant.Post_AuditMode)
public class AuditPostMode extends AbstractAuditMode {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditPostMode.class);

    private RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);

    @Override
    public void afterCheckPassYs(Recipe recipe) {
        super.afterCheckPassYs(recipe);
        recipeService.afterCheckPassYs(recipe);
    }

    @Override
    public void afterCheckNotPassYs(Recipe recipe) { recipeService.afterCheckNotPassYs(recipe);
    }

    @Override
    @LogRecord
    public void afterPayChange(Boolean saveFlag, Recipe dbRecipe, RecipeResultBean result, Map<String, Object> attrMap) {
        //默认审核通过
        Integer status = RecipeStatusConstant.CHECK_PASS;
        RecipeDAO recipeDAO = getDAO(RecipeDAO.class);
        Integer giveMode = null == MapValueUtil.getInteger(attrMap,"giveMode") ? dbRecipe.getGiveMode() : MapValueUtil.getInteger(attrMap,"giveMode");
        Integer payFlag = MapValueUtil.getInteger(attrMap, "payFlag");
        // 获取paymode
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder byOrderCode = orderDAO.getByOrderCode(dbRecipe.getOrderCode());
        Integer payMode = byOrderCode.getPayMode();
        //根据传入的方式来处理, 因为供应商列表，钥世圈提供的有可能是多种方式都支持，当时这2个值是保存为null的
        if (saveFlag) {
            attrMap.put("chooseFlag", 1);
            String memo = "";
            if (RecipeBussConstant.GIVEMODE_SEND_TO_HOME.equals(giveMode)) {
                if (RecipeBussConstant.PAYMODE_ONLINE.equals(payMode)) {
                    //线上支付
                    if (PayConstant.PAY_FLAG_PAY_SUCCESS == payFlag) {
                        //配送到家-线上支付
                        status = RecipeStatusConstant.READY_CHECK_YS;
                        memo = "配送到家-线上支付成功";
                    } else {
                        memo = "配送到家-线上支付失败";
                    }
                }
                else if (RecipeBussConstant.PAYMODE_OFFLINE.equals(payMode)) {
                    //货到付款添加支付成功后修改状态
                    if (PayConstant.PAY_FLAG_PAY_SUCCESS == payFlag) {
                        status = RecipeStatusConstant.READY_CHECK_YS;
                        memo = "配送到家-货到付款成功";
                    }
                }
            } else if (RecipeBussConstant.GIVEMODE_TO_HOS.equals(giveMode)) {
                //医院取药-线上支付，这块其实已经用不到了
                //添加支付成功后修改状态
                if(PayConstant.PAY_FLAG_PAY_SUCCESS == payFlag){
                    status = RecipeStatusConstant.READY_CHECK_YS;
                    memo = "医院取药-线上支付部分费用(除药品费)成功";
                }
            } else if (RecipeBussConstant.GIVEMODE_TFDS.equals(giveMode)) {
                //添加支付成功后修改状态
                if(PayConstant.PAY_FLAG_PAY_SUCCESS == payFlag){
                    status = RecipeStatusConstant.READY_CHECK_YS;
                    memo = "药店取药-线上支付部分费用(除药品费)成功";
                }

            }else if (RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE.equals(giveMode)){
                if(PayConstant.PAY_FLAG_PAY_SUCCESS == payFlag){
                    status = RecipeStatusConstant.READY_CHECK_YS;
                }
            }
            //记录日志
            RecipeLogService.saveRecipeLog(dbRecipe.getRecipeId(), RecipeStatusConstant.CHECK_PASS, status, memo);
        } else {
            attrMap.put("chooseFlag", 0);
            if (RecipeBussConstant.FROMFLAG_HIS_USE.equals(dbRecipe.getFromflag())) {
                status = dbRecipe.getStatus();
            }
        }
        super.updateRecipeInfoByRecipeId(dbRecipe.getRecipeId(), status, attrMap, result);
        Recipe recipe = recipeDAO.getByRecipeId(dbRecipe.getRecipeId());
        LOGGER.info("afterPayChange recipe:{}", JSON.toJSONString(recipe));
        if (saveFlag && RecipeResultBean.SUCCESS.equals(result.getCode())) {
            //更新处方为待审核
            if (RecipeStatusEnum.RECIPE_STATUS_READY_CHECK_YS.getType().equals(status)) {
                dbRecipe.setCheckFlag(0);
                recipeDAO.updateNonNullFieldByPrimaryKey(dbRecipe);
                RecipeMsgService.batchSendMsg(dbRecipe.getRecipeId(), status);
                super.startRecipeAuditProcess(dbRecipe.getRecipeId());
            }
            //设置新的审方状态
            super.setAuditStateToPendingReview(dbRecipe,status);
            if (RecipeStatusConstant.CHECK_PASS_YS == status) {
                //说明是可进行医保支付的单子或者是中药或膏方处方
                RemoteDrugEnterpriseService remoteDrugEnterpriseService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
                remoteDrugEnterpriseService.pushSingleRecipeInfo(dbRecipe.getRecipeId());
            }
        }
        individuationHandle(result, dbRecipe, status);
    }

    private void individuationHandle(RecipeResultBean result, Recipe dbRecipe, Integer status) {
        if (RecipeResultBean.SUCCESS.equals(result.getCode())) {
            if (RecipeStatusConstant.READY_CHECK_YS == status) {
                RedisClient redisClient = RedisClient.instance();
                //目前只有水果湖社区医院在用
                Set<String> organIdList = redisClient.sMembers(CacheConstant.KEY_SKIP_YSCHECK_LIST);
                if (CollectionUtils.isNotEmpty(organIdList) && organIdList.contains(dbRecipe.getClinicOrgan().toString())) {
                    RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
                    //跳过人工审核
                    CheckYsInfoBean checkResult = new CheckYsInfoBean();
                    checkResult.setRecipeId(dbRecipe.getRecipeId());
                    checkResult.setCheckDoctorId(dbRecipe.getDoctor());
                    checkResult.setCheckOrganId(dbRecipe.getClinicOrgan());
                    try {
                        recipeService.autoPassForCheckYs(checkResult);
                    } catch (Exception e) {
                        LOGGER.error("updateRecipePayResultImplForOrder 药师自动审核失败. recipeId={}", dbRecipe.getRecipeId(),e);
                        RecipeLogService.saveRecipeLog(dbRecipe.getRecipeId(), dbRecipe.getStatus(), status,
                                "updateRecipePayResultImplForOrder 药师自动审核失败:" + e.getMessage());
                    }
                } else {
                    if (RecipeBussConstant.FROMFLAG_HIS_USE.equals(dbRecipe.getFromflag())) {
                        //进行身边医生消息推送
                        RecipeMsgService.sendRecipeMsg(RecipeMsgEnum.RECIPE_YS_READYCHECK_4HIS, dbRecipe);
                    }
                }
            }
        }
    }
}
