package recipe.service;

import com.alibaba.fastjson.JSON;
import com.ngari.recipe.vo.FastRecipeReq;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import com.ngari.recipe.vo.FastRecipeDetailVO;
import com.ngari.recipe.vo.FastRecipeVO;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.event.GlobalEventExecFactory;
import eh.cdr.api.vo.MedicalDetailBean;
import eh.utils.ValidateUtil;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.business.BaseService;
import recipe.client.DocIndexClient;
import recipe.client.OperationClient;
import recipe.constant.RecipeBussConstant;
import recipe.core.api.IFastRecipeBusinessService;
import recipe.core.api.patient.IPatientBusinessService;
import recipe.dao.*;
import recipe.enumerate.status.RecipeStatusEnum;
import recipe.enumerate.type.BussSourceTypeEnum;
import recipe.hisservice.RecipeToHisCallbackService;
import recipe.vo.doctor.RecipeInfoVO;

import java.math.BigDecimal;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.stream.Collectors;

/**
 * @Description
 * @Author yzl
 * @Date 2022-08-16
 */
@RpcBean(value = "fastRecipeService")
public class FastRecipeService extends BaseService implements IFastRecipeBusinessService {

    private static final Logger logger = LoggerFactory.getLogger(RecipeToHisCallbackService.class);

    @Autowired
    private FastRecipeDAO fastRecipeDAO;

    @Autowired
    private FastRecipeDetailDAO fastRecipeDetailDAO;

    @Autowired
    private IPatientBusinessService recipePatientService;

    @Autowired
    private RecipeDAO recipeDAO;

    @Autowired
    private RecipeExtendDAO recipeExtendDAO;

    @Autowired
    private RecipeDetailDAO recipeDetailDAO;

    @Autowired
    private DocIndexClient docIndexClient;

    @Autowired
    private OperationClient operationClient;

    @Override
    public List<Integer> fastRecipeSaveRecipeList(List<RecipeInfoVO> recipeInfoVOList) {
        List<FutureTask<Integer>> futureTasks = new LinkedList<>();
        for (RecipeInfoVO recipeInfoVO : recipeInfoVOList) {
            FutureTask<Integer> futureTask = new FutureTask<>(() -> fastRecipeSaveRecipe(recipeInfoVO));
            futureTasks.add(futureTask);
            GlobalEventExecFactory.instance().getExecutor().submit(futureTask);
        }
        List<Integer> resultList = super.futureTaskCallbackBeanList(futureTasks, 15000);

        ExecutorService singleExecutor = Executors.newSingleThreadExecutor();
        singleExecutor.execute(() -> {
            if (CollectionUtils.isNotEmpty(resultList)) {
                for (Integer recipeId : resultList) {
                    recipePatientService.esignRecipeCa(recipeId);
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        logger.error("fastRecipeSaveRecipeList sleep error", e);
                    }
                }
            }
        });
        return resultList;
    }

    private Integer fastRecipeSaveRecipe(RecipeInfoVO recipeInfoVO) {
        try {
            //1.参数设置默认值
            RecipeBean recipeBean = recipeInfoVO.getRecipeBean();
            recipeBean.setStatus(RecipeStatusEnum.RECIPE_STATUS_UNSIGNED.getType());
            recipeBean.setRecipeSourceType(0);
            recipeBean.setSignDate(new Date());
            recipeBean.setRecipeMode(RecipeBussConstant.RECIPEMODE_NGARIHEALTH);
            recipeBean.setChooseFlag(0);
            recipeBean.setGiveFlag(0);
            recipeBean.setPayFlag(0);
            recipeBean.setPushFlag(0);
            recipeBean.setRemindFlag(0);
            recipeBean.setTakeMedicine(0);
            recipeBean.setPatientStatus(1);
            recipeBean.setStatus(2);
            recipeBean.setFromflag(1);
            recipeBean.setRecipeSourceType(1);
            recipeBean.setReviewType(1);
            recipeBean.setAuditState(5);
            recipeBean.setProcessState(0);
            recipeBean.setSubState(0);
            recipeBean.setSupportMode(0);
            recipeBean.setGiveMode(2);
            recipeBean.setFastRecipeFlag(1);
            recipeBean.setBussSource(BussSourceTypeEnum.BUSSSOURCE_REVISIT.getType());
            int buyNum = ValidateUtil.nullOrZeroInteger(recipeInfoVO.getBuyNum()) ? 1 : recipeInfoVO.getBuyNum();
            packageTotalParamByBuyNum(recipeInfoVO, buyNum);
            Integer recipeId = recipePatientService.saveRecipe(recipeInfoVO);
            //recipePatientService.esignRecipeCa(recipeId);
            recipePatientService.updateRecipeIdByConsultId(recipeId, recipeInfoVO.getRecipeBean().getClinicId());
            return recipeId;
        } catch (Exception e) {
            logger.error("fastRecipeSaveRecipe error", e);
        }
        return null;
    }


    /**
     * 便捷购药 运营平台添加药方
     *
     * @param recipeId,title
     * @param title
     * @return
     */
    @Override
    public Integer addFastRecipe(Integer recipeId, String title) {
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipeId);
        if (Objects.isNull(recipe) || Objects.isNull(recipeExtend)) {
            throw new DAOException("未找到对应处方单！");
        }
        operationClient.isAuthorisedOrgan(recipe.getClinicOrgan());
        //1.保存药方
        FastRecipe fastRecipe = new FastRecipe();
        fastRecipe.setIntroduce("");
        fastRecipe.setBackgroundImg("");
        fastRecipe.setStatus(1);
        fastRecipe.setMinNum(0);
        fastRecipe.setMaxNum(null);
        fastRecipe.setOrderNum(0);
        fastRecipe.setClinicOrgan(recipe.getClinicOrgan());
        fastRecipe.setOrganName(recipe.getOrganName());
        fastRecipe.setActualPrice(recipe.getActualPrice());
        fastRecipe.setCopyNum(recipe.getCopyNum());
        fastRecipe.setDecoctionId(recipeExtend.getDecoctionId());
        fastRecipe.setDecoctionPrice(recipeExtend.getDecoctionPrice());
        fastRecipe.setDecoctionText(recipeExtend.getDecoctionText());
        if (Objects.nonNull(recipeExtend.getDocIndexId())) {
            MedicalDetailBean medicalDetailBean = docIndexClient.getEmrMedicalDetail(recipeExtend.getDocIndexId());
            if (Objects.nonNull(medicalDetailBean) && CollectionUtils.isNotEmpty(medicalDetailBean.getDetailList())) {
                fastRecipe.setDocText(JSONUtils.toString(medicalDetailBean.getDetailList()));
            }
        }
        fastRecipe.setFromFlag(recipeExtend.getFromFlag());
        fastRecipe.setGiveMode(recipe.getGiveMode());
        fastRecipe.setJuice(recipeExtend.getJuice());
        fastRecipe.setJuiceUnit(recipeExtend.getJuiceUnit());
        fastRecipe.setMakeMethodId(recipeExtend.getMakeMethodId());
        fastRecipe.setMakeMethodText(recipeExtend.getMakeMethodText());
        fastRecipe.setMemo(recipe.getMemo());
        fastRecipe.setMinor(recipeExtend.getMinor());
        fastRecipe.setMinorUnit(recipeExtend.getMinorUnit());
        fastRecipe.setOfflineRecipeName(recipe.getOfflineRecipeName());
        fastRecipe.setRecipeType(recipe.getRecipeType());
        fastRecipe.setSymptomId(recipeExtend.getSymptomId());
        fastRecipe.setSymptomName(recipeExtend.getSymptomName());
        fastRecipe.setTitle(title);
        fastRecipe.setTotalMoney(recipe.getTotalMoney());
        fastRecipe.setEveryTcmNumFre(recipeExtend.getEveryTcmNumFre());
        fastRecipe.setDoctorIsDecoction(recipeExtend.getDoctorIsDecoction());
        fastRecipe.setNeedQuestionnaire(0);
        fastRecipe.setRecipeMemo(recipe.getRecipeMemo());
        FastRecipe fastRecipeResult = fastRecipeDAO.save(fastRecipe);

        //2.保存药方详情
        List<Recipedetail> recipeDetailList = recipeDetailDAO.findByRecipeId(recipeId);
        if (CollectionUtils.isNotEmpty(recipeDetailList)) {
            for (Recipedetail recipedetail : recipeDetailList) {
                FastRecipeDetail fastRecipeDetail = BeanUtils.map(recipedetail, FastRecipeDetail.class);
                fastRecipeDetail.setFastRecipeId(fastRecipeResult.getId());
                fastRecipeDetailDAO.save(fastRecipeDetail);
            }
        }
        return fastRecipeResult.getId();
    }


    /**
     * 根据购买数量处理总价，剂量等数据
     *
     * @param recipeInfoVO
     * @param buyNum
     */
    private void packageTotalParamByBuyNum(RecipeInfoVO recipeInfoVO, int buyNum) {
        logger.info("packageTotalParamByBuyNum recipeInfoVO = {}, buyNum = [{}] ", JSON.toJSONString(recipeInfoVO), buyNum);
        if (buyNum == 1) {
            return;
        }
        //1. 处理recipe表相关字段
        RecipeBean recipeBean = recipeInfoVO.getRecipeBean();
        //中药剂数
        if (ValidateUtil.notNullAndZeroInteger(recipeBean.getCopyNum())) {
            recipeBean.setCopyNum(recipeBean.getCopyNum() * buyNum);
        }
        //处方金额
        if (Objects.nonNull(recipeBean.getTotalMoney())) {
            recipeBean.setTotalMoney(recipeBean.getTotalMoney().multiply(BigDecimal.valueOf(buyNum)));
        }
        //最后需支付费用
        if (Objects.nonNull(recipeBean.getActualPrice())) {
            recipeBean.setActualPrice(recipeBean.getActualPrice().multiply(BigDecimal.valueOf(buyNum)));
        }

        //2. 处理recipeDetail表相关字段
        List<RecipeDetailBean> recipeDetailBeanList = recipeInfoVO.getRecipeDetails();
        if (CollectionUtils.isNotEmpty(recipeDetailBeanList)) {
            for (RecipeDetailBean recipeDetailBean : recipeDetailBeanList) {
                //药物使用总数量
                if (Objects.nonNull(recipeDetailBean.getUseTotalDose())) {
                    recipeDetailBean.setUseTotalDose(recipeDetailBean.getUseTotalDose() * buyNum);
                }
                //药物发放数量
                if (Objects.nonNull(recipeDetailBean.getSendNumber())) {
                    recipeDetailBean.setSendNumber(recipeDetailBean.getSendNumber() * buyNum);
                }
                //药物使用天数
                if (Objects.nonNull(recipeDetailBean.getUseDays())) {
                    recipeDetailBean.setUseDays(recipeDetailBean.getUseDays() * buyNum);
                }
                //药物金额
                if (Objects.nonNull(recipeDetailBean.getDrugCost())) {
                    recipeDetailBean.setDrugCost(recipeDetailBean.getDrugCost().multiply(BigDecimal.valueOf(buyNum)));
                }
            }
        }

    }

    /**
     * 查询药方列表
     *
     * @param fastRecipeReq
     * @return
     */
    @Override
    public List<FastRecipe> findFastRecipeListByParam(FastRecipeReq fastRecipeReq) {
        return fastRecipeDAO.findFastRecipeListByParam(fastRecipeReq);
    }

    @Override
    public List<FastRecipeDetail> findFastRecipeDetailsByFastRecipeId(Integer fastRecipeId) {
        return fastRecipeDetailDAO.findFastRecipeDetailsByFastRecipeId(fastRecipeId);
    }

    @Override
    public Boolean simpleUpdateFastRecipe(FastRecipeVO fastRecipeVO) {
        FastRecipe fastRecipe = fastRecipeDAO.get(fastRecipeVO.getId());
        if (Objects.isNull(fastRecipe)) {
            throw new DAOException("未找到对应药方单！");
        } else {
            if (!operationClient.isAuthorisedOrgan(fastRecipe.getClinicOrgan())) {
                throw new DAOException("您没有修改该药方的权限！");
            }
            if (Objects.nonNull(fastRecipeVO.getOrderNum())) {
                fastRecipe.setOrderNum(fastRecipeVO.getOrderNum());
            }
            if (Objects.nonNull(fastRecipeVO.getMaxNum())) {
                fastRecipe.setMaxNum(fastRecipeVO.getMaxNum());
            }
            if (Objects.nonNull(fastRecipeVO.getMinNum())) {
                fastRecipe.setMinNum(fastRecipeVO.getMinNum());
            }
            if (Objects.nonNull(fastRecipeVO.getStatus())) {
                fastRecipe.setStatus(fastRecipeVO.getStatus());
            }

            fastRecipeDAO.update(fastRecipe);
        }
        return true;
    }

    @Override
    public Boolean fullUpdateFastRecipe(FastRecipeVO fastRecipeVO) {
        //1.更新药方
        FastRecipe fastRecipe = fastRecipeDAO.get(fastRecipeVO.getId());
        if (Objects.isNull(fastRecipe)) {
            throw new DAOException("未找到对应药方单！");
        } else {
            if (!operationClient.isAuthorisedOrgan(fastRecipe.getClinicOrgan())) {
                throw new DAOException("您没有修改该药方的权限！");
            }
            fastRecipe.setBackgroundImg(fastRecipeVO.getBackgroundImg());
            fastRecipe.setIntroduce(fastRecipeVO.getIntroduce());
            fastRecipe.setNeedQuestionnaire(fastRecipeVO.getNeedQuestionnaire());
            fastRecipe.setQuestionnaireUrl(fastRecipeVO.getQuestionnaireUrl());
            fastRecipeDAO.update(fastRecipe);
        }
        //1.更新药方详情（目前只能删除药品，修改药品随后版本做）
        List<FastRecipeDetailVO> fastRecipeDetailVOList = fastRecipeVO.getFastRecipeDetailList();
        if (CollectionUtils.isEmpty(fastRecipeDetailVOList)) {
            throw new DAOException("最少添加一种药品！");
        }
        List<Integer> fastRecipeDetailIds = fastRecipeDetailVOList.stream().map(FastRecipeDetailVO::getId).collect(Collectors.toList());
        List<FastRecipeDetail> fastRecipeDetailList = fastRecipeDetailDAO.findFastRecipeDetailsByFastRecipeId(fastRecipe.getId());
        if (CollectionUtils.isEmpty(fastRecipeDetailList)) {
            return true;
        }
        for (FastRecipeDetail fastRecipeDetail : fastRecipeDetailList) {
            if (!fastRecipeDetailIds.contains(fastRecipeDetail.getId())) {
                //更新为删除
                fastRecipeDetailDAO.updateStatusById(fastRecipeDetail.getId(), 0);
            }
        }
        return true;
    }
}
