package recipe.audit.auditmode;

import com.google.common.collect.ImmutableMap;
import com.ngari.home.asyn.model.BussCreateEvent;
import com.ngari.home.asyn.service.IAsynDoBussService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.entity.DrugsEnterprise;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.persistence.DAOFactory;
import eh.base.constant.BussTypeConstant;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ApplicationUtils;
import recipe.constant.RecipeBussConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.constant.ReviewTypeConstant;
import recipe.dao.OrganAndDrugsepRelationDAO;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeDetailDAO;
import recipe.drugsenterprise.RemoteDrugEnterpriseService;
import recipe.recipecheck.HisCheckRecipeService;
import recipe.service.RecipeLogService;
import recipe.service.RecipeMsgService;
import recipe.service.RecipeService;
import recipe.service.RecipeServiceSub;

import java.util.List;

import static ctd.persistence.DAOFactory.getDAO;

/**
 * created by shiyuping on 2019/8/15
 * 审方前置
 */
@AuditMode(ReviewTypeConstant.Pre_AuditMode)
public class AuditPreMode extends AbstractAuidtMode {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuditPreMode.class);

    @Override
    public void afterHisCallBackChange(Integer status, Recipe recipe, String memo) {
        if (status == RecipeStatusConstant.CHECK_PASS) {
            //暂时去掉，没有用到
            /*//todo 判断是否是杭州市医保患者，医保患者得医保信息回传后才能设置待审核
            if (RecipeServiceSub.isMedicalPatient(recipe.getMpiid(),recipe.getClinicOrgan())){
                //医保上传确认中----三天后没回传就设置成已取消
                status = RecipeStatusConstant.CHECKING_MEDICAL_INSURANCE;
            }else {
                status = RecipeStatusConstant.READY_CHECK_YS;
            }*/
            status = RecipeStatusConstant.READY_CHECK_YS;
        }
        // 平台模式前置需要发送卡片
        //if (RecipeBussConstant.FROMFLAG_PLATFORM.equals(recipe.getFromflag())){
        //待审核只有平台发
        if (RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipe.getRecipeMode())) {
            RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            RecipeServiceSub.sendRecipeTagToPatient(recipe, detailDAO.findByRecipeId(recipe.getRecipeId()), null, true);
        }
        //}
        //生成文件成功后再去更新处方状态
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(), status, null);
        //日志记录
        RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), status, memo);
        //发送消息
        sendMsg(status, recipe, memo);
        //针对his审方的模式,先在此处处理,推送消息给前置机,让前置机取轮询HIS获取审方结果
        HisCheckRecipeService hisCheckRecipeService = ApplicationUtils.getRecipeService(HisCheckRecipeService.class);
        hisCheckRecipeService.sendCheckRecipeInfo(recipe);
    }

    private void sendMsg(Integer status, Recipe recipe, String memo) {
        //平台处方进行消息发送等操作
        if (1 == recipe.getFromflag()) {
            //发送消息--待审核消息
            RecipeMsgService.batchSendMsg(recipe.getRecipeId(), status);
            //平台审方途径下才发消息
            if (status == RecipeStatusConstant.READY_CHECK_YS && new Integer(1).equals(recipe.getCheckMode())) {
                if (RecipeBussConstant.RECIPEMODE_NGARIHEALTH.equals(recipe.getRecipeMode())) {
                    //增加药师首页待处理任务---创建任务
                    RecipeBean recipeBean = ObjectCopyUtils.convert(recipe, RecipeBean.class);
                    ApplicationUtils.getBaseService(IAsynDoBussService.class).fireEvent(new BussCreateEvent(recipeBean, BussTypeConstant.RECIPE));
                }

            }
            //保存至电子病历
            RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
            recipeService.saveRecipeDocIndex(recipe);
        }
    }

    @Override
    public int afterAuditRecipeChange() {
        return RecipeStatusConstant.CHECK_PASS;
    }

    @Override
    public void afterCheckPassYs(Recipe recipe) {
        RecipeDetailDAO detailDAO = getDAO(RecipeDetailDAO.class);
        Integer recipeId = recipe.getRecipeId();
        String recipeMode = recipe.getRecipeMode();
        //正常平台处方
        if (RecipeBussConstant.FROMFLAG_PLATFORM.equals(recipe.getFromflag())) {
            //审核通过只有互联网发
            if (RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipeMode)) {
                RecipeServiceSub.sendRecipeTagToPatient(recipe, detailDAO.findByRecipeId(recipeId), null, true);
                //向患者推送处方消息
                RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.CHECK_PASS);
                /*//同步到互联网监管平台
                SyncExecutorService syncExecutorService = ApplicationUtils.getRecipeService(SyncExecutorService.class);
                syncExecutorService.uploadRecipeIndicators(recipe);*/
            } else {
                //平台前置发送审核通过消息
                //向患者推送处方消息
                //处方通知您有一张处方单需要处理，请及时查看。
                RecipeMsgService.batchSendMsg(recipe, RecipeStatusConstant.CHECK_PASS_YS);
            }
            //临沭人民医院个性化处理
            if (new Integer(1002753).equals(recipe.getClinicOrgan())){
                OrganAndDrugsepRelationDAO dao = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
                List<DrugsEnterprise> enterprises = dao.findDrugsEnterpriseByOrganIdAndStatus(1002753, 1);
                if (CollectionUtils.isNotEmpty(enterprises)){
                    RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
                    recipeDAO.updateRecipeInfoByRecipeId(recipe.getRecipeId(), ImmutableMap.of("enterpriseId",enterprises.get(0).getId()));
                }
                //审核后处方推送到药企
                RemoteDrugEnterpriseService remoteDrugEnterpriseService = ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);
                remoteDrugEnterpriseService.pushSingleRecipeInfo(recipe.getRecipeId());
            }
        }
        RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "审核通过处理完成");
    }
}
