package recipe.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import com.ngari.recipe.recipe.model.RecipeExtendBean;
import com.ngari.recipe.vo.FastRecipeDetailVO;
import com.ngari.recipe.vo.FastRecipeReq;
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
import recipe.enumerate.type.RecipeDrugFormTypeEnum;
import recipe.enumerate.type.RecipeTypeEnum;
import recipe.hisservice.RecipeToHisCallbackService;
import recipe.serviceprovider.recipe.service.RemoteRecipeService;
import recipe.vo.doctor.RecipeInfoVO;

import java.math.BigDecimal;
import java.util.*;
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

    @Autowired
    private RemoteRecipeService remoteRecipeService;


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
                    recipePatientService.fastRecipeCa(recipeId);
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

    /**
     * 此处最优方案为前端组装患者信息和需要患者选择的参数，其他参数后端从药方获取，
     * 目前前端去组装的参数，但是没传全，暂时后台查询补全
     *
     * @param recipeInfoVO
     * @return
     */
    private Integer fastRecipeSaveRecipe(RecipeInfoVO recipeInfoVO) {
        try {
            FastRecipe fastRecipe = fastRecipeDAO.get(recipeInfoVO.getMouldId());
            if (Objects.isNull(fastRecipe)) {
                return null;
            }
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
            recipeBean.setRecipeDrugForm(fastRecipe.getRecipeDrugForm());
            recipeBean.setBussSource(BussSourceTypeEnum.BUSSSOURCE_REVISIT.getType());
            recipeBean.setDecoctionNum(fastRecipe.getDecoctionNum());

            recipeBean.setRecipeMemo(fastRecipe.getRecipeMemo());

            RecipeExtendBean recipeExtendBean = recipeInfoVO.getRecipeExtendBean();
            recipeExtendBean.setMakeMethodId(fastRecipe.getMakeMethodId());
            recipeExtendBean.setMakeMethodText(fastRecipe.getMakeMethodText());
            recipeExtendBean.setJuice(fastRecipe.getJuice());
            recipeExtendBean.setJuiceUnit(fastRecipe.getJuiceUnit());
            recipeExtendBean.setDecoctionId(fastRecipe.getDecoctionId());
            recipeExtendBean.setDecoctionText(fastRecipe.getDecoctionText());
            recipeExtendBean.setSingleOrCompoundRecipe(fastRecipe.getSingleOrCompoundRecipe());

            int buyNum = ValidateUtil.nullOrZeroInteger(recipeInfoVO.getBuyNum()) ? 1 : recipeInfoVO.getBuyNum();
            packageTotalParamByBuyNum(recipeInfoVO, buyNum);
            Integer recipeId = recipePatientService.saveRecipe(recipeInfoVO);
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
        fastRecipe.setDecoctionNum(recipe.getDecoctionNum());
        if (Objects.nonNull(recipe.getRecipeDrugForm())) {
            fastRecipe.setRecipeDrugForm(recipe.getRecipeDrugForm());
        }
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
        fastRecipe.setRequirementsForTakingId(recipeExtend.getRequirementsForTakingId());
        fastRecipe.setRequirementsForTakingCode(recipeExtend.getRequirementsForTakingCode());
        fastRecipe.setRequirementsForTakingText(recipeExtend.getRequirementsForTakingText());
        fastRecipe.setDoctorIsDecoction(recipeExtend.getDoctorIsDecoction());
        fastRecipe.setNeedQuestionnaire(0);
        fastRecipe.setRecipeMemo(recipe.getRecipeMemo());
        fastRecipe.setSingleOrCompoundRecipe(recipeExtend.getSingleOrCompoundRecipe());
        FastRecipe fastRecipeResult = fastRecipeDAO.save(fastRecipe);

        //2.保存药方详情
        List<Recipedetail> recipeDetailList = recipeDetailDAO.findByRecipeId(recipeId);
        if (CollectionUtils.isNotEmpty(recipeDetailList)) {
            for (Recipedetail recipedetail : recipeDetailList) {
                if (RecipeTypeEnum.RECIPETYPE_TCM.getType().equals(recipe.getRecipeType())) {
                    recipedetail.setDrugForm(RecipeDrugFormTypeEnum.getDrugForm(recipe.getRecipeDrugForm()));
                }
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
            fastRecipe.setTitle(fastRecipeVO.getTitle());
            fastRecipe.setOfflineRecipeName(fastRecipeVO.getTitle());
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
        //更新保密方标识
        if (Integer.valueOf(1).equals(fastRecipeVO.getSecrecyFlag())) {
            fastRecipeDetailDAO.updateTypeByFastRecipeId(fastRecipe.getId(), 3);
        } else {
            fastRecipeDetailDAO.updateTypeByFastRecipeId(fastRecipe.getId(), 1);
        }

        return true;
    }

    @Override
    public void handleFastRecipeOldData(String paramName) {
        JSONArray jsonArray;
        if ("1".equals(paramName)) {
            jsonArray = JSON.parseArray("[{\"introduce\":\"1. 【药品名称】酸梅汤2.【作用类别】解暑、生津、开胃3.【适应症】夏暑之季，多汗口渴，胃口欠佳等。4.【用法用量】洗净后浸泡半小时，将浸泡好的药材连水倒入锅中，加1500-2000ml水，大火煮开后，转小火煮40分钟关火，加适量冰糖搅拌至融化后焖10分钟，最后撒上桂花。5.【禁忌】尚不明确6.【注意事项】(1)桂花少许（自备）、冰糖适量（自备）。(2)胃溃疡及慢性胃病患者慎用。(3)糖尿病患者及有高血压、心脏病、肝病、肾病等慢性病严重者应在医师指导下用药。(4)儿童、妇女、哺乳期妇女、年老体弱应在医师指导下用药。(5)儿童必须在成人监护下使用。(6)对本品过敏者禁用，过敏体质者慎用。(7)如正在使用其他药品，使用本品前请咨询。7.【药物互相作用】如与其他药物同时使用可能会发生药物相互作用，详情请咨询医师或药师。8.【不良反应】尚不明确\",\"title\":\"乌梅汤（酸梅汤）（1帖）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"酸梅汤\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"暑病\",\"code\":\"ZX76077\"}]}],\"backgroundImg\":\"630485a5a4a7331c38ff8feb\",\"mouldId\":101,\"maxNum\":28,\"recipeBean\":{\"actualPrice\":0.76,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":3,\"copyNum\":1,\"totalMoney\":0.76,\"giveMode\":2,\"fromflag\":1,\"offlineRecipeName\":\"酸梅汤\",\"recipeExtend\":{\"makeMethodId\":\"42\",\"makeMethodText\":\"普煎二汁各200mL\",\"decoctionId\":\"165\",\"decoctionText\":\"无需代煎\"}},\"detailBeanList\":[{\"saleName\":\"桔红\",\"drugId\":5264644,\"organDrugCode\":\"69248\",\"drugName\":\"陈皮\",\"drugSpec\":\"3g,6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":6,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"prn\",\"usePathways\":\"po\",\"organUsingRate\":\"10\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"必要时\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":6,\"useDays\":1,\"drugCost\":0.117,\"salePrice\":0.0195,\"drugCode\":\"69248\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69248\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"陈皮 3g,6g,9gj/g\",\"type\":3},{\"saleName\":\"[甲]乌梅\",\"drugId\":5264872,\"organDrugCode\":\"69439\",\"drugName\":\"乌梅\",\"drugSpec\":\"6g,9g,30j\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":10,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"prn\",\"usePathways\":\"po\",\"organUsingRate\":\"10\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"必要时\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":10,\"useDays\":1,\"drugCost\":0.363,\"salePrice\":0.0363,\"drugCode\":\"69439\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69439\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"乌梅 6g,9g,30j/g\",\"type\":3},{\"saleName\":\"甘草片\",\"drugId\":5264404,\"organDrugCode\":\"69058\",\"drugName\":\"甘草\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":5,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"prn\",\"usePathways\":\"po\",\"organUsingRate\":\"10\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"必要时\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":5,\"useDays\":1,\"drugCost\":0.1895,\"salePrice\":0.0379,\"drugCode\":\"69058\",\"status\":1,\"producer\":\"内蒙古省\",\"producerCode\":\"69058\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"甘草 3g,5gj/g\",\"type\":3},{\"saleName\":\"[甲]生山楂\",\"drugId\":5264415,\"organDrugCode\":\"69069\",\"drugName\":\"生山楂\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":5,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"prn\",\"usePathways\":\"po\",\"organUsingRate\":\"10\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"必要时\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":5,\"useDays\":1,\"drugCost\":0.0905,\"salePrice\":0.0181,\"drugCode\":\"69069\",\"status\":1,\"producer\":\"山东\",\"producerCode\":\"69069\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"生山楂 6g,10g,15gj/g\",\"type\":3}]},{\"introduce\":\"1.【药品名称】解暑生津1号 2.【作用类别】解暑化湿，生津止渴3.【适应症】夏暑之季，胃口不佳，口咽干燥等4.【用法用量】每天两次，1次1包，开水冲服，儿童半量，婴幼儿1/3量。\",\"title\":\"解暑生津1号（1帖）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"解暑生津1号\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"暑病\",\"code\":\"ZX76077\"}]}],\"backgroundImg\":\"630485cadaf658361ddf8237\",\"mouldId\":102,\"maxNum\":28,\"recipeBean\":{\"actualPrice\":8.24,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":3,\"copyNum\":1,\"totalMoney\":8.24,\"giveMode\":2,\"fromflag\":1,\"recipeExtend\":{\"makeMethodId\":\"42\",\"makeMethodText\":\"普煎二汁各200mL\",\"decoctionId\":\"165\",\"decoctionText\":\"无需代煎\"}},\"detailBeanList\":[{\"saleName\":\"石斛特优二\",\"drugId\":5271978,\"organDrugCode\":\"69472\",\"drugName\":\"浙石斛\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":1,\"drugCost\":4.7604,\"salePrice\":1.1901,\"drugCode\":\"69472\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69472\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"浙石斛 6g,9gj/g\",\"type\":3},{\"saleName\":\"甘草片\",\"drugId\":5264404,\"organDrugCode\":\"69058\",\"drugName\":\"甘草\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":1,\"drugCost\":0.1516,\"salePrice\":0.0379,\"drugCode\":\"69058\",\"status\":1,\"producer\":\"内蒙古省\",\"producerCode\":\"69058\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"甘草 3g,5gj/g\",\"type\":3},{\"saleName\":\"麦冬\",\"drugId\":5264902,\"organDrugCode\":\"69412\",\"drugName\":\"浙麦冬\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":1,\"drugCost\":2.496,\"salePrice\":0.832,\"drugCode\":\"69412\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69412\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"浙麦冬 6g,9gj/g\",\"type\":3},{\"saleName\":\"苏薄荷\",\"drugId\":5271922,\"organDrugCode\":\"69541\",\"drugName\":\"薄荷\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":1,\"drugCost\":0.0528,\"salePrice\":0.0264,\"drugCode\":\"69541\",\"status\":1,\"producer\":\"江苏\",\"producerCode\":\"69541\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"薄荷 3g,5gj/g\",\"type\":3},{\"saleName\":\"双花\",\"drugId\":5264676,\"organDrugCode\":\"69265\",\"drugName\":\"金银花\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":1,\"drugCost\":1.4512,\"salePrice\":0.3628,\"drugCode\":\"69265\",\"status\":1,\"producer\":\"山东\",\"producerCode\":\"69265\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"金银花 6g,10g,15gj/g\",\"type\":3},{\"saleName\":\"[甲]扁豆花\",\"drugId\":5264634,\"organDrugCode\":\"69300\",\"drugName\":\"扁豆花\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":1,\"drugCost\":0.2516,\"salePrice\":0.1258,\"drugCode\":\"69300\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69300\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"扁豆花 3g,5gj/g\",\"type\":3},{\"saleName\":\"桔红\",\"drugId\":5264644,\"organDrugCode\":\"69248\",\"drugName\":\"陈皮\",\"drugSpec\":\"3g,6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":1,\"drugCost\":0.078,\"salePrice\":0.0195,\"drugCode\":\"69248\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69248\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"陈皮 3g,6g,9gj/g\",\"type\":3},{\"saleName\":\"杞子\",\"drugId\":5264757,\"organDrugCode\":\"69236\",\"drugName\":\"枸杞子\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":1,\"drugCost\":0.3452,\"salePrice\":0.0863,\"drugCode\":\"69236\",\"status\":1,\"producer\":\"宁夏\",\"producerCode\":\"69236\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"枸杞子 6g,9gj/g\",\"type\":3},{\"saleName\":\"乌元参\",\"drugId\":5271951,\"organDrugCode\":\"69511\",\"drugName\":\"玄参\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":1,\"drugCost\":0.1104,\"salePrice\":0.0276,\"drugCode\":\"69511\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69511\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"玄参 6g,9gj/g\",\"type\":3},{\"saleName\":\"藿香\",\"drugId\":5264770,\"organDrugCode\":\"69282\",\"drugName\":\"广藿香\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":1,\"drugCost\":0.176,\"salePrice\":0.044,\"drugCode\":\"69282\",\"status\":1,\"producer\":\"广东\",\"producerCode\":\"69282\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"广藿香 6g,9gj/g\",\"type\":3},{\"saleName\":\"荷花叶\",\"drugId\":5264850,\"organDrugCode\":\"69377\",\"drugName\":\"荷叶\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":1,\"drugCost\":0.0308,\"salePrice\":0.0154,\"drugCode\":\"69377\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69377\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"荷叶 6g,10g,15gj/g\",\"type\":3}]},{\"introduce\":\"1.【药品名称】咳平颗粒2.【作用类别】清热宣肺，化痰止咳。用于感冒、咳嗽、支气管炎等呼吸道疾病。3.【用法用量】开水冲服。一次15g，一日3次；或遵医嘱。\",\"title\":\"咳平颗粒\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"咳平颗粒\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"咳嗽\",\"code\":\"ZX76070\"}]}],\"backgroundImg\":\"630485f4daf658361ddf8260\",\"mouldId\":103,\"maxNum\":5,\"recipeBean\":{\"actualPrice\":97.3,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":2,\"copyNum\":0,\"recipeExtend\":{},\"totalMoney\":97.3,\"giveMode\":2,\"fromflag\":1},\"detailBeanList\":[{\"saleName\":\"[乙]j@咳平颗粒\",\"drugId\":5070798,\"organDrugCode\":\"10240\",\"drugName\":\"j@咳平颗粒\",\"drugSpec\":\"自制15g*10\",\"pack\":10,\"drugUnit\":\"袋\",\"useDose\":15,\"defaultUseDose\":15,\"useDoseStr\":\"\",\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"tid\",\"usePathways\":\"po\",\"organUsingRate\":\"3\",\"organUsePathways\":\"1\",\"usingRateTextFromHis\":\"3次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":1,\"useDays\":3,\"drugCost\":97.3,\"salePrice\":97.3,\"status\":1,\"producer\":\"杭州地区\",\"producerCode\":\"10240\",\"useDaysB\":\"3\",\"drugType\":2,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":772,\"pharmacyName\":\"中成药\",\"drugDisplaySplicedName\":\"j@咳平颗粒 自制15g*10/袋\",\"drugDisplaySplicedSaleName\":\"[乙]j@咳平颗粒\",\"type\":1}]},{\"introduce\":\"1.【药品名称】复方仙灵脾消癥颗粒2.【作用类别】舒肝理气，化瘀散结。用于肝郁气滞，血瘀痰凝引起的乳腺增生。3.【用法用量】开水冲服。一次10g，一日2次;或遵医嘱。\",\"title\":\"复方仙灵脾消癥颗粒\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"复方仙灵脾消癥颗粒\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"乳癖\",\"code\":\"ZX10796\"}]}],\"backgroundImg\":\"63048614b7ba5c3b80064ece\",\"mouldId\":104,\"maxNum\":5,\"recipeBean\":{\"actualPrice\":197.6,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":2,\"recipeExtend\":{},\"copyNum\":0,\"totalMoney\":197.6,\"giveMode\":2,\"fromflag\":1},\"detailBeanList\":[{\"saleName\":\"[乙]@复方仙灵脾消癥颗粒（原消增颗粒\",\"drugId\":5070842,\"organDrugCode\":\"10468\",\"drugName\":\"@复方仙灵脾消癥颗粒（原消增颗\",\"drugSpec\":\"14包\",\"pack\":14,\"drugUnit\":\"袋\",\"useDose\":10,\"defaultUseDose\":10,\"useDoseStr\":\"\",\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"mr2c\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"1\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":1,\"useDays\":7,\"drugCost\":197.6,\"salePrice\":197.6,\"status\":1,\"producer\":\"自制\",\"producerCode\":\"10468\",\"useDaysB\":\"7\",\"drugType\":2,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":772,\"pharmacyName\":\"中成药\",\"drugDisplaySplicedName\":\"@复方仙灵脾消癥颗粒（原消增颗 14包/袋\",\"drugDisplaySplicedSaleName\":\"[乙]@复方仙灵脾消癥颗粒（原消增颗粒\",\"type\":1}]},{\"introduce\":\"1.【药品名称】和营止痛颗粒2.【作用类别】补气养阴，和营止痛。用于老年之腰臀疼痛。3.【用法用量】饭后温开水冲服。一次6g，一日3次。\",\"title\":\"和营止痛颗粒\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"和营止痛颗粒\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"腰痛\",\"code\":\"ZX10466\"}]}],\"backgroundImg\":\"6304867ca4a7331c38ff90a6\",\"mouldId\":105,\"maxNum\":5,\"recipeBean\":{\"actualPrice\":90.4,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":2,\"copyNum\":0,\"recipeExtend\":{},\"totalMoney\":90.4,\"giveMode\":2,\"fromflag\":1},\"detailBeanList\":[{\"saleName\":\"[乙]@和营止痛颗粒（原腰臀筋膜炎颗粒\",\"drugId\":5070840,\"organDrugCode\":\"10206\",\"drugName\":\"@和营止痛颗粒（原腰臀筋膜炎颗\",\"drugSpec\":\"无糖6g*10包\",\"pack\":10,\"drugUnit\":\"袋\",\"useDose\":6,\"defaultUseDose\":6,\"useDoseStr\":\"\",\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"tid\",\"usePathways\":\"po\",\"organUsingRate\":\"3\",\"organUsePathways\":\"1\",\"usingRateTextFromHis\":\"3次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":1,\"useDays\":3,\"drugCost\":90.4,\"salePrice\":90.4,\"status\":1,\"producer\":\"自制\",\"producerCode\":\"10206\",\"useDaysB\":\"3\",\"drugType\":2,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":772,\"pharmacyName\":\"中成药\",\"drugDisplaySplicedName\":\"@和营止痛颗粒（原腰臀筋膜炎颗 无糖6g*10包/袋\",\"drugDisplaySplicedSaleName\":\"[乙]@和营止痛颗粒（原腰臀筋膜炎颗粒\",\"type\":1}]},{\"introduce\":\"1.【药品名称】复方大青颗粒2.【作用类别】清热解毒、宣透表邪。用于流感，急性扁桃体炎、咽炎、流行性腮腺炎等，并对病毒性疾患有预防作用。3.【用法用量】开水冲服。一次6g，一日3次，重症加倍，小儿酌减；或遵医嘱。\",\"title\":\"复方大青颗粒\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"复方大青颗粒\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"时行感冒\",\"code\":\"ZX10361\"}]}],\"backgroundImg\":\"63048695b2e78e773e1235eb\",\"mouldId\":106,\"maxNum\":5,\"recipeBean\":{\"actualPrice\":36.44,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":2,\"copyNum\":0,\"recipeExtend\":{},\"totalMoney\":36.44,\"giveMode\":2,\"fromflag\":1},\"detailBeanList\":[{\"saleName\":\"[乙]@复方大青颗粒(无糖型）\",\"drugId\":5070760,\"organDrugCode\":\"37141\",\"drugName\":\"@复方大青颗粒(无糖型）\",\"drugSpec\":\"6g*10包\",\"pack\":10,\"drugUnit\":\"袋\",\"useDose\":6,\"defaultUseDose\":6,\"useDoseStr\":\"\",\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"tid\",\"usePathways\":\"po\",\"organUsingRate\":\"3\",\"organUsePathways\":\"1\",\"usingRateTextFromHis\":\"3次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":1,\"useDays\":3,\"drugCost\":36.44,\"salePrice\":36.44,\"status\":1,\"producer\":\"自制\",\"producerCode\":\"37141\",\"useDaysB\":\"3\",\"drugType\":2,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":772,\"pharmacyName\":\"中成药\",\"drugDisplaySplicedName\":\"@复方大青颗粒(无糖型） 6g*10包/袋\",\"drugDisplaySplicedSaleName\":\"[乙]@复方大青颗粒(无糖型）\",\"type\":1}]},{\"introduce\":\"1.【药品名称】痛风胶囊2.【作用类别】清热泄浊。用于痛风引起的高尿酸血症。3.【用法用量】口服。一次2～4粒，一日3次。\",\"title\":\"痛风胶囊\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"痛风胶囊\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"痛风\",\"code\":\"ZX10493\"}]}],\"backgroundImg\":\"630486c0a4a7331c38ff90f4\",\"mouldId\":107,\"maxNum\":5,\"recipeBean\":{\"actualPrice\":31.05,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":2,\"copyNum\":0,\"recipeExtend\":{},\"totalMoney\":31.05,\"giveMode\":2,\"fromflag\":1},\"detailBeanList\":[{\"saleName\":\"[乙]@痛风胶囊\",\"drugId\":5070800,\"organDrugCode\":\"10257\",\"drugName\":\"@痛风胶囊\",\"drugSpec\":\"自制0.35*40s\",\"pack\":40,\"drugUnit\":\"瓶\",\"useDose\":2,\"defaultUseDose\":0.35,\"useDoseStr\":\"\",\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"tid\",\"usePathways\":\"po\",\"organUsingRate\":\"3\",\"organUsePathways\":\"1\",\"usingRateTextFromHis\":\"3次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":1,\"useDays\":7,\"drugCost\":31.05,\"salePrice\":31.05,\"status\":1,\"producer\":\"杭州地区\",\"producerCode\":\"10257\",\"useDaysB\":\"7\",\"drugType\":2,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":772,\"pharmacyName\":\"中成药\",\"drugDisplaySplicedName\":\"@痛风胶囊 自制0.35*40s/瓶\",\"drugDisplaySplicedSaleName\":\"[乙]@痛风胶囊\",\"type\":1}]}]");
        } else if (("2".equals(paramName))) {
            jsonArray = JSON.parseArray("[{\"introduce\":\"1.【药品名称】活血生发胶囊2.【作用类别】活血祛瘀，养血生发。用于脂秃、斑秃、全秃、普秃、病后秃发等症。3.【用法用量】口服。一次5粒，一日3次。或遵医嘱。\",\"title\":\"活血生发胶囊（1瓶）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"活血生发胶囊\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"油风\",\"code\":\"ZX10770\"}]}],\"backgroundImg\":\"630486dadaf658361ddf834e\",\"mouldId\":108,\"maxNum\":5,\"recipeBean\":{\"actualPrice\":28.3,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":2,\"copyNum\":0,\"recipeExtend\":{},\"totalMoney\":28.3,\"giveMode\":2,\"fromflag\":1},\"detailBeanList\":[{\"saleName\":\"[乙]@活血生发胶囊(原名生发一号）\",\"drugId\":5070799,\"organDrugCode\":\"10241\",\"drugName\":\"@活血生发胶囊(原名生发一号）\",\"drugSpec\":\"自制0.4g*40s\",\"pack\":40,\"drugUnit\":\"瓶\",\"useDose\":5,\"defaultUseDose\":0.4,\"useDoseStr\":\"\",\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"tid\",\"usePathways\":\"po\",\"organUsingRate\":\"3\",\"organUsePathways\":\"1\",\"usingRateTextFromHis\":\"3次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":1,\"useDays\":2,\"drugCost\":28.3,\"salePrice\":28.3,\"status\":1,\"producer\":\"杭州地区\",\"producerCode\":\"10241\",\"useDaysB\":\"2\",\"drugType\":2,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":772,\"pharmacyName\":\"中成药\",\"drugDisplaySplicedName\":\"@活血生发胶囊(原名生发一号） 自制0.4g*40s/瓶\",\"drugDisplaySplicedSaleName\":\"[乙]@活血生发胶囊(原名生发一号）\",\"type\":1}]},{\"introduce\":\"1.【药品名称】化浊祛瘟外用1号2.【作用类别】醒脑开窍、避瘟除秽、驱蚊防虫3.【适应症】预防夏季暑湿感冒，蚊虫叮咬4.【用法用量】佩戴或置于卧室、车辆内，待香味消失后进行更换。5.【禁忌】尚不明确6.【注意事项】（1）化浊祛瘟外用1号用药需遵循中医辨证论治的原则，审证求因，辨证用药，方可取得较为满意的效果。（2）处方内的辛香类中草药较多，对于孕妇，不宜随身佩戴，否则可能引发滑胎等不良后果。（3）驱蚊草药多含有芳香物和挥发油，有可能引起皮肤和黏膜的过敏反应，所以过敏体质的人群也不适合贴身佩戴。（4）药粉香气一般能维持一月左右，需定期更换。7.【药物互相作用】尚不明确8.【不良反应】尚不明确\",\"title\":\"化浊祛瘟外用1号（1帖）\",\"mouldId\":109,\"maxNum\":28,\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"互联网医院便捷购药\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"时行感冒\",\"code\":\"ZX10361\"}]}],\"backgroundImg\":\"630486fcb7ba5c3b80064fc0\",\"recipeBean\":{\"actualPrice\":8.6,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":3,\"copyNum\":1,\"totalMoney\":8.6,\"giveMode\":2,\"fromflag\":1,\"offlineRecipeName\":\"化浊祛瘟外用1号\",\"recipeExtend\":{\"makeMethodId\":\"42\",\"makeMethodText\":\"普煎二汁各200mL\",\"decoctionId\":\"165\",\"decoctionText\":\"无需代煎\"}},\"detailBeanList\":[{\"saleName\":\"[甲]艾叶[限9g]\",\"drugId\":5264836,\"organDrugCode\":\"69387\",\"drugName\":\"艾叶[限9g]\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":20,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":20,\"useDays\":3,\"drugCost\":0.47,\"salePrice\":0.0235,\"status\":1,\"producer\":\"安徽\",\"producerCode\":\"69387\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"艾叶[限9g] 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"杭白芷\",\"drugId\":5264385,\"organDrugCode\":\"69037\",\"drugName\":\"白芷\",\"drugSpec\":\"6g,10gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":10,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":10,\"useDays\":3,\"drugCost\":0.338,\"salePrice\":0.0338,\"status\":1,\"producer\":\"四川\",\"producerCode\":\"69037\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"白芷 6g,10gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"苏薄荷\",\"drugId\":5271922,\"organDrugCode\":\"69541\",\"drugName\":\"薄荷\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":15,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":15,\"useDays\":3,\"drugCost\":0.396,\"salePrice\":0.0264,\"status\":1,\"producer\":\"江苏\",\"producerCode\":\"69541\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"薄荷 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"关防风\",\"drugId\":5264844,\"organDrugCode\":\"69359\",\"drugName\":\"防风\",\"drugSpec\":\"3g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":5,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":5,\"useDays\":3,\"drugCost\":3.4215,\"salePrice\":0.6843,\"status\":1,\"producer\":\"内蒙\",\"producerCode\":\"69359\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"防风 3g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"公丁香\",\"drugId\":5264827,\"organDrugCode\":\"69344\",\"drugName\":\"丁香\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":5,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":5,\"useDays\":3,\"drugCost\":0.67,\"salePrice\":0.134,\"status\":1,\"producer\":\"云南\",\"producerCode\":\"69344\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"丁香 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"藿香\",\"drugId\":5264770,\"organDrugCode\":\"69282\",\"drugName\":\"广藿香\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":20,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":20,\"useDays\":3,\"drugCost\":0.898,\"salePrice\":0.0449,\"status\":1,\"producer\":\"广东\",\"producerCode\":\"69282\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"广藿香 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]桂枝\",\"drugId\":5264631,\"organDrugCode\":\"69259\",\"drugName\":\"桂枝\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":10,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":10,\"useDays\":3,\"drugCost\":0.13,\"salePrice\":0.013,\"status\":1,\"producer\":\"广西\",\"producerCode\":\"69259\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"桂枝 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"机冰\",\"drugId\":5271904,\"organDrugCode\":\"69568\",\"drugName\":\"冰片\",\"drugSpec\":\"1gj\",\"pack\":1,\"drugUnit\":\"G\",\"useDose\":1,\"defaultUseDose\":1,\"useDoseUnit\":\"G\",\"dosageUnit\":\"G\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":1,\"useDays\":3,\"drugCost\":0.77,\"salePrice\":0.77,\"status\":1,\"producer\":\"云南\",\"producerCode\":\"69568\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"冰片 1gj/G\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]山奈\",\"drugId\":5271907,\"organDrugCode\":\"69565\",\"drugName\":\"山奈\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":5,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":5,\"useDays\":3,\"drugCost\":0.441,\"salePrice\":0.0882,\"status\":1,\"producer\":\"广西\",\"producerCode\":\"69565\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"山奈 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"菖蒲\",\"drugId\":5264649,\"organDrugCode\":\"69266\",\"drugName\":\"石菖蒲\",\"drugSpec\":\"5g,12gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":10,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":10,\"useDays\":3,\"drugCost\":0.908,\"salePrice\":0.0908,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69266\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"石菖蒲 5g,12gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"安南桂\",\"drugId\":5271986,\"organDrugCode\":\"69464\",\"drugName\":\"肉桂\",\"drugSpec\":\"2g,3gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":2,\"useDays\":3,\"drugCost\":0.1,\"salePrice\":0.05,\"status\":1,\"producer\":\"广东\",\"producerCode\":\"69464\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"肉桂 2g,3gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"谷茴\",\"drugId\":5271934,\"organDrugCode\":\"69529\",\"drugName\":\"小茴香\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":3,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"16105500\",\"usePathways\":\"55zj\",\"organUsingRate\":\"14\",\"organUsePathways\":\"16\",\"usingRateTextFromHis\":\"每月一次\",\"usePathwaysTextFromHis\":\"除去外包装\",\"useTotalDose\":3,\"useDays\":3,\"drugCost\":0.0711,\"salePrice\":0.0237,\"status\":1,\"producer\":\"甘肃\",\"producerCode\":\"69529\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"小茴香 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3}]},{\"introduce\":\"1.【药品名称】健脾祛湿1号2.【作用类别】健脾理气，芳香化湿3.【适应症】湿气较重、乏力、胃口不佳、大便溏稀等脾虚湿盛者。4.【用法用量】每天2次，1次1包，开水冲服，儿童半量，婴幼儿1/3量。5.【禁忌】尚不明确6.【注意事项】（1）忌烟酒、辛辣、鱼腥食物。（2）糖尿病患者及有高血压、心脏病、肝病、肾病等慢性病严重者应在医师指导下用药。（3）儿童、妇女、哺乳期妇女、年老体弱应在医师指导下用药。（4）儿童必须在成人监护下使用。（5）对本品过敏者禁用，过敏体质者慎用。（6）如正在使用其他药品，使用本品前请咨询。7.【药物互相作用】如与其他药物同时使用可能会发生药物相互作用，详情请咨询医师或药师。8.【不良反应】尚不明确\",\"title\":\"健脾祛湿1号（1帖）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"健脾祛湿1号\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"胃痞病\",\"code\":\"ZX10555\"}]},{\"key\":\"tcmSyndrome\",\"name\":\"中医证候\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"脾虚湿阻证\",\"code\":\"ZX50115\"}]}],\"mouldId\":110,\"maxNum\":28,\"backgroundImg\":\"6304890fdaf658361ddf854e\",\"recipeBean\":{\"actualPrice\":3.46,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":3,\"copyNum\":1,\"totalMoney\":3.46,\"giveMode\":2,\"fromflag\":1,\"offlineRecipeName\":\"健脾祛湿1号\",\"recipeExtend\":{\"makeMethodId\":\"42\",\"makeMethodText\":\"普煎二汁各200mL\",\"decoctionId\":\"165\",\"decoctionText\":\"无需代煎\"}},\"detailBeanList\":[{\"saleName\":\"佛手片\",\"drugId\":5264403,\"organDrugCode\":\"69057\",\"drugName\":\"佛手\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":3,\"drugCost\":0.1806,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0903,\"status\":1,\"producer\":\"广东\",\"producerCode\":\"69057\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"佛手 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"桔红\",\"drugId\":5264644,\"organDrugCode\":\"69248\",\"drugName\":\"陈皮\",\"drugSpec\":\"3g,6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.078,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0195,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69248\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"陈皮 3g,6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"文元党\",\"drugId\":5264479,\"organDrugCode\":\"69139\",\"drugName\":\"党参\",\"drugSpec\":\"10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":6,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":6,\"useDays\":3,\"drugCost\":0.9942,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.1657,\"status\":1,\"producer\":\"甘肃\",\"producerCode\":\"69139\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"党参 10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"云苓\",\"drugId\":5264664,\"organDrugCode\":\"69245\",\"drugName\":\"茯苓\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.1764,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0441,\"status\":1,\"producer\":\"安徽\",\"producerCode\":\"69245\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"茯苓 6g,10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"冬术\",\"drugId\":5264401,\"organDrugCode\":\"69054\",\"drugName\":\"麸炒白术\",\"drugSpec\":\"6g,10g,12g15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.2324,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0581,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69054\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"麸炒白术 6g,10g,12g15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"炒苡仁\",\"drugId\":5264527,\"organDrugCode\":\"69249\",\"drugName\":\"麸炒薏苡仁\",\"drugSpec\":\"10g,12g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.142,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0355,\"status\":1,\"producer\":\"贵州\",\"producerCode\":\"69249\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"麸炒薏苡仁 10g,12g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]芡实\",\"drugId\":5264432,\"organDrugCode\":\"69086\",\"drugName\":\"芡实\",\"drugSpec\":\"10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":6,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":6,\"useDays\":3,\"drugCost\":0.6624,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.1104,\"status\":1,\"producer\":\"安徽\",\"producerCode\":\"69086\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"芡实 10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"阳春砂\",\"drugId\":5271955,\"organDrugCode\":\"69507\",\"drugName\":\"砂仁[阳]\",\"drugSpec\":\"3g,5g,6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":3,\"drugCost\":1.2874,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.6437,\"status\":1,\"producer\":\"广东\",\"producerCode\":\"69507\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"砂仁[阳] 3g,5g,6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]玫瑰花\",\"drugId\":5264422,\"organDrugCode\":\"69076\",\"drugName\":\"玫瑰花\",\"drugSpec\":\"6g,10gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":3,\"drugCost\":0.2414,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.1207,\"status\":1,\"producer\":\"山东\",\"producerCode\":\"69076\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"玫瑰花 6g,10gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]生山楂\",\"drugId\":5264415,\"organDrugCode\":\"69069\",\"drugName\":\"生山楂\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.0724,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0181,\"status\":1,\"producer\":\"山东\",\"producerCode\":\"69069\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"生山楂 6g,10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]甜叶菊\",\"drugId\":5271911,\"organDrugCode\":\"69552\",\"drugName\":\"甜叶菊\",\"drugSpec\":\"1gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":1,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":1,\"useDays\":3,\"drugCost\":0.0517,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0517,\"status\":1,\"producer\":\"广东\",\"producerCode\":\"69552\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"甜叶菊 1gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3}]},{\"introduce\":\"1.【药品名称】利咽生津1号2.【作用类别】解毒利咽，生津润喉3.【适应症】咽干、咽痛、咽燥、咽痒、咽喉异物感等。4.【用法用量】每天2次，1次1包，开水冲服，儿童半量，婴幼儿1/3量。5.【禁忌】尚不明确6.【注意事项】（1）忌烟酒、辛辣、鱼腥食物。（2）糖尿病患者及有高血压、心脏病、肝病、肾病等慢性病严重者应在医师指导下用药。（3）儿童、妇女、哺乳期妇女、年老体弱应在医师指导下用药。（4）儿童必须在成人监护下使用。（5）对本品过敏者禁用，过敏体质者慎用。（6）如正在使用其他药品，使用本品前请咨询。7.【药物互相作用】如与其他药物同时使用可能会发生药物相互作用，详情请咨询医师或药师。8.【不良反应】尚不明确\",\"title\":\"利咽生津1号（1帖）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"利咽生津1号\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"胃痞病\",\"code\":\"ZX10555\"}]}],\"mouldId\":111,\"maxNum\":28,\"backgroundImg\":\"63048932b2e78e773e12385b\",\"recipeBean\":{\"actualPrice\":7.8,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":3,\"copyNum\":1,\"totalMoney\":7.8,\"giveMode\":2,\"fromflag\":1,\"offlineRecipeName\":\"利咽生津1号\",\"recipeExtend\":{\"makeMethodId\":\"42\",\"makeMethodText\":\"普煎二汁各200mL\",\"decoctionId\":\"165\",\"decoctionText\":\"无需代煎\"}},\"detailBeanList\":[{\"saleName\":\"生甘草\",\"drugId\":5264404,\"organDrugCode\":\"69058\",\"drugName\":\"甘草\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.1516,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0379,\"status\":1,\"producer\":\"内蒙古省\",\"producerCode\":\"69058\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"甘草 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"桔红\",\"drugId\":5264644,\"organDrugCode\":\"69248\",\"drugName\":\"陈皮\",\"drugSpec\":\"3g,6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.078,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0195,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69248\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"陈皮 3g,6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"麦门冬\",\"drugId\":5264902,\"organDrugCode\":\"69412\",\"drugName\":\"浙麦冬\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":6,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":6,\"useDays\":3,\"drugCost\":1.6,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.2667,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69412\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"浙麦冬 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"元参\",\"drugId\":5271951,\"organDrugCode\":\"69511\",\"drugName\":\"玄参\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.1104,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0276,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69511\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"玄参 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"苦桔梗\",\"drugId\":5264412,\"organDrugCode\":\"69066\",\"drugName\":\"桔梗\",\"drugSpec\":\"3g,6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.336,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.084,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69066\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"桔梗 3g,6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"空沙参\",\"drugId\":5264650,\"organDrugCode\":\"69297\",\"drugName\":\"南沙参\",\"drugSpec\":\"6g,10,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.4856,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.1214,\"status\":1,\"producer\":\"贵州\",\"producerCode\":\"69297\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"南沙参 6g,10,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"玉蝴蝶\",\"drugId\":5264837,\"organDrugCode\":\"69397\",\"drugName\":\"木蝴蝶\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":3,\"drugCost\":0.1018,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0509,\"status\":1,\"producer\":\"广西\",\"producerCode\":\"69397\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"木蝴蝶 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"生山栀\",\"drugId\":5272050,\"organDrugCode\":\"69119\",\"drugName\":\"栀子\",\"drugSpec\":\"6g,10gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.1724,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0431,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69119\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"栀子 6g,10gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"石斛特二\",\"drugId\":5271978,\"organDrugCode\":\"69472\",\"drugName\":\"浙石斛\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":4.7604,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":1.1901,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69472\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"浙石斛 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]胖大海\",\"drugId\":5271903,\"organDrugCode\":\"69569\",\"drugName\":\"胖大海\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":3,\"drugCost\":0.3766,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.1883,\"status\":1,\"producer\":\"海南\",\"producerCode\":\"69569\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"胖大海 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3}]},{\"introduce\":\"1.【药品名称】明目润眼1号2.【作用类别】清热养肝、滋阴健脾3.【适应症】眼干、眼涩、畏光，痒痛不适，不耐久视，白睛红赤隐隐，或兼口干、小便黄、大便干等。4.【用法用量】每天1包，开水冲服代茶饮。具体方法：第一泡，建议沸水冲泡后熏眼，待温度适宜后饮用，而后续水同茶饮用。5.【禁忌】尚不明确6.【注意事项】（1）忌烟酒、辛辣、鱼腥食物。（2）糖尿病患者及有高血压、心脏病、肝病、肾病等慢性病严重者应在医师指导下用药。（3）儿童、哺乳期妇女、年老体弱应在医师指导下用药。（4）儿童必须在成人监护下使用。（5）对本品过敏者禁用，过敏体质者慎用。（6）如正在使用其他药品，使用本品前请咨询。7.【药物互相作用】如与其他药物同时使用可能会发生药物相互作用，详情请咨询医师或药师。8.【不良反应】尚不明确\",\"title\":\"明目润眼1号（1帖）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"明目润眼1号\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"胃痞病\",\"code\":\"ZX10555\"}]}],\"mouldId\":112,\"maxNum\":28,\"backgroundImg\":\"63048950b7ba5c3b800651e3\",\"recipeBean\":{\"actualPrice\":1.63,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":3,\"copyNum\":1,\"totalMoney\":1.63,\"giveMode\":2,\"fromflag\":1,\"offlineRecipeName\":\"明目润眼1号\",\"recipeExtend\":{\"makeMethodId\":\"42\",\"makeMethodText\":\"普煎二汁各200mL\",\"decoctionId\":\"165\",\"decoctionText\":\"无需代煎\"}},\"detailBeanList\":[{\"saleName\":\"怀菊\",\"drugId\":5264892,\"organDrugCode\":\"69380\",\"drugName\":\"菊花\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":3,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":3,\"useDays\":3,\"drugCost\":0.2487,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0829,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69380\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"菊花 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]密蒙花\",\"drugId\":5264917,\"organDrugCode\":\"69448\",\"drugName\":\"密蒙花\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":3,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":3,\"useDays\":3,\"drugCost\":0.147,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0489,\"status\":1,\"producer\":\"湖北\",\"producerCode\":\"69448\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"密蒙花 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"草决明\",\"drugId\":5264800,\"organDrugCode\":\"69383\",\"drugName\":\"决明子\",\"drugSpec\":\"10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":6,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":6,\"useDays\":3,\"drugCost\":0.1212,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0202,\"status\":1,\"producer\":\"广西\",\"producerCode\":\"69383\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"决明子 10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"麦门冬\",\"drugId\":5264902,\"organDrugCode\":\"69412\",\"drugName\":\"浙麦冬\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":3,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":3,\"useDays\":3,\"drugCost\":0.8001,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.2667,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69412\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"浙麦冬 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"桔红\",\"drugId\":5264644,\"organDrugCode\":\"69248\",\"drugName\":\"陈皮\",\"drugSpec\":\"3g,6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":3,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":3,\"useDays\":3,\"drugCost\":0.0585,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0195,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69248\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"陈皮 3g,6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"杞子\",\"drugId\":5264757,\"organDrugCode\":\"69236\",\"drugName\":\"枸杞子\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":5,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":5,\"useDays\":3,\"drugCost\":0.4315,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0863,\"status\":1,\"producer\":\"宁夏\",\"producerCode\":\"69236\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"枸杞子 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3}]},{\"introduce\":\"1.【药品名称】平肝降脂1号2.【作用类别】平肝明目，降脂化浊3.【适应症】肥胖、高血脂、眼睛疲劳、头晕、大便偏干等肝脾不和者。4.【用法用量】每天2次，1次1包，开水冲服，儿童半量，婴幼儿1/3量。5.【禁忌】尚不明确6.【注意事项】（1）忌烟酒、辛辣、鱼腥食物。（2）糖尿病患者及有高血压、心脏病、肝病、肾病等慢性病严重者应在医师指导下用药。（3）儿童、妇女、哺乳期妇女、年老体弱应在医师指导下用药。（4）儿童必须在成人监护下使用。（5）对本品过敏者禁用，过敏体质者慎用。（6）如正在使用其他药品，使用本品前请咨询。7.【药物互相作用】如与其他药物同时使用可能会发生药物相互作用，详情请咨询医师或药师。8.【不良反应】尚不明确\",\"title\":\"平肝降脂1号（1帖）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"平肝降脂1号\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"胃痞病\",\"code\":\"ZX10555\"}]}],\"mouldId\":113,\"maxNum\":28,\"backgroundImg\":\"6304896fb2e78e773e1238a6\",\"recipeBean\":{\"actualPrice\":2.85,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":3,\"copyNum\":1,\"totalMoney\":2.85,\"giveMode\":2,\"fromflag\":1,\"offlineRecipeName\":\"平肝降脂1号\",\"recipeExtend\":{\"makeMethodId\":\"42\",\"makeMethodText\":\"普煎二汁各200mL\",\"decoctionId\":\"165\",\"decoctionText\":\"无需代煎\"}},\"detailBeanList\":[{\"saleName\":\"紫丹参\",\"drugId\":5272077,\"organDrugCode\":\"69052\",\"drugName\":\"丹参\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":6,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":6,\"useDays\":3,\"drugCost\":0.3102,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0517,\"status\":1,\"producer\":\"山东\",\"producerCode\":\"69052\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"丹参 6g,10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"怀菊\",\"drugId\":5264892,\"organDrugCode\":\"69380\",\"drugName\":\"菊花\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":3,\"drugCost\":0.166,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0829,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69380\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"菊花 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"杞子\",\"drugId\":5264757,\"organDrugCode\":\"69236\",\"drugName\":\"枸杞子\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":6,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":6,\"useDays\":3,\"drugCost\":0.5178,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0863,\"status\":1,\"producer\":\"宁夏\",\"producerCode\":\"69236\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"枸杞子 6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"桔红\",\"drugId\":5264644,\"organDrugCode\":\"69248\",\"drugName\":\"陈皮\",\"drugSpec\":\"3g,6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":0.078,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0195,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69248\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"陈皮 3g,6g,9gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"荷花叶\",\"drugId\":5264850,\"organDrugCode\":\"69377\",\"drugName\":\"荷叶\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":2,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":2,\"useDays\":3,\"drugCost\":0.0308,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0154,\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69377\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"荷叶 6g,10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"草决明\",\"drugId\":5264800,\"organDrugCode\":\"69383\",\"drugName\":\"决明子\",\"drugSpec\":\"10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":3,\"useDays\":3,\"drugCost\":0.0606,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0202,\"status\":1,\"producer\":\"广西\",\"producerCode\":\"69383\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"决明子 10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]生山楂\",\"drugId\":5264415,\"organDrugCode\":\"69069\",\"drugName\":\"生山楂\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":6,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":6,\"useDays\":3,\"drugCost\":0.1086,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0181,\"status\":1,\"producer\":\"山东\",\"producerCode\":\"69069\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"生山楂 6g,10g,15gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]三七片[60头]\",\"drugId\":5264840,\"organDrugCode\":\"69429\",\"drugName\":\"三七片[60头]\",\"drugSpec\":\"3g,5gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":4,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":4,\"useDays\":3,\"drugCost\":2.1576,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.5394,\"status\":1,\"producer\":\"云南\",\"producerCode\":\"69429\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"三七片[60头] 3g,5gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3},{\"saleName\":\"[甲]甜叶菊\",\"drugId\":5271911,\"organDrugCode\":\"69552\",\"drugName\":\"甜叶菊\",\"drugSpec\":\"1gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":1,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"bid\",\"usePathways\":\"po\",\"organUsingRate\":\"2\",\"organUsePathways\":\"01\",\"usingRateTextFromHis\":\"2次/日\",\"usePathwaysTextFromHis\":\"口服\",\"useTotalDose\":1,\"useDays\":3,\"drugCost\":0.0517,\"entrustmentId\":\"69\",\"memo\":\"\",\"drugEntrustCode\":\"1\",\"salePrice\":0.0517,\"status\":1,\"producer\":\"广东\",\"producerCode\":\"69552\",\"useDaysB\":\"3\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":770,\"pharmacyName\":\"小包装\",\"drugDisplaySplicedName\":\"甜叶菊 1gj/g\",\"drugDisplaySplicedSaleName\":\"\",\"type\":3}]},{\"detailBeanList\":[{\"drugCost\":0.68,\"drugId\":5296549,\"drugName\":\"炒牛蒡子k\",\"drugSpec\":\"6g,10gK\",\"drugType\":3,\"drugUnit\":\"g\",\"organDrugCode\":\"63974\",\"usingRate\":\"bid\",\"organUsingRate\":\"2\",\"usingRateText\":\"开水冲服\",\"useDays\":7,\"pack\":1,\"saleName\":\"牛蒡子k\",\"salePrice\":0.068,\"useDose\":10,\"DefaultUseDose\":6,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"useTotalDose\":10,\"usePathways\":\"po\",\"organUsePathways\":\"01\",\"usePathwaysText\":\"口服\",\"drugEntrustCode\":\"1\",\"entrustmentId\":69,\"status\":1,\"producer\":\"浙江景岳堂药业\",\"producerCode\":\"63974\",\"useDaysB\":\"7\",\"drugDisplaySplicedName\":\"[甲]炒牛蒡子K 6g,10gK/g\",\"type\":3,\"pharmacyId\":769,\"pharmacyName\":\"颗粒剂\"},{\"drugCost\":6.077,\"drugId\":5296056,\"drugName\":\"防风k\",\"drugSpec\":\"1\",\"drugType\":3,\"drugUnit\":\"g\",\"organDrugCode\":\"63470\",\"usingRate\":\"bid\",\"organUsingRate\":\"2\",\"usingRateText\":\"2次/日\",\"useDays\":7,\"pack\":1,\"saleName\":\"[乙]防风k\",\"salePrice\":0.6077,\"useDose\":10,\"DefaultUseDose\":3,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"useTotalDose\":10,\"usePathways\":\"po\",\"organUsePathways\":\"01\",\"usePathwaysText\":\"口服\",\"drugEntrustCode\":\"1\",\"entrustmentId\":69,\"status\":1,\"producer\":\"浙江景岳堂药业\",\"producerCode\":\"63470\",\"useDaysB\":\"7\",\"drugDisplaySplicedName\":\"[甲]防风K 1/g\",\"type\":3,\"pharmacyId\":769,\"pharmacyName\":\"颗粒剂\"},{\"drugCost\":1.1712,\"drugId\":5295944,\"drugName\":\"黄芪k\",\"drugSpec\":\"1\",\"drugType\":3,\"drugUnit\":\"g\",\"organDrugCode\":\"63356\",\"usingRate\":\"bid\",\"organUsingRate\":\"2\",\"usingRateText\":\"2次/日\",\"useDays\":7,\"pack\":1,\"saleName\":\"[乙]黄芪k\",\"salePrice\":0.0976,\"useDose\":10,\"DefaultUseDose\":3,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"useTotalDose\":12,\"usePathways\":\"po\",\"organUsePathways\":\"01\",\"usePathwaysText\":\"口服\",\"drugEntrustCode\":\"1\",\"entrustmentId\":69,\"status\":1,\"producer\":\"浙江景岳堂药业\",\"producerCode\":\"63356\",\"useDaysB\":\"7\",\"drugDisplaySplicedName\":\"[乙]黄芪k 1/g\",\"type\":3,\"pharmacyId\":769,\"pharmacyName\":\"颗粒剂\"},{\"drugCost\":0.1895,\"drugId\":5295911,\"drugName\":\"甘草k\",\"drugSpec\":\"1\",\"drugType\":3,\"drugUnit\":\"g\",\"organDrugCode\":\"63323\",\"usingRate\":\"bid\",\"organUsingRate\":\"2\",\"usingRateText\":\"2次/日\",\"useDays\":7,\"pack\":1,\"saleName\":\"[乙]甘草k\",\"salePrice\":0.0379,\"useDose\":10,\"DefaultUseDose\":3,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"useTotalDose\":5,\"usePathways\":\"po\",\"organUsePathways\":\"01\",\"usePathwaysText\":\"口服\",\"drugEntrustCode\":\"1\",\"entrustmentId\":69,\"status\":1,\"producer\":\"浙江景岳堂药业\",\"producerCode\":\"63323\",\"useDaysB\":\"7\",\"drugDisplaySplicedName\":\"[甲]甘草K\",\"type\":3,\"pharmacyId\":769,\"pharmacyName\":\"颗粒剂\"},{\"drugCost\":1.105,\"drugId\":5296405,\"drugName\":\"连翘k\",\"drugSpec\":\"10g,15g,30g\",\"drugType\":3,\"drugUnit\":\"g\",\"organDrugCode\":\"63830\",\"usingRate\":\"bid\",\"organUsingRate\":\"2\",\"usingRateText\":\"2次/日\",\"useDays\":7,\"pack\":1,\"saleName\":\"[甲]连翘k\",\"salePrice\":0.1105,\"useDose\":10,\"DefaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"useTotalDose\":10,\"usePathways\":\"po\",\"organUsePathways\":\"01\",\"usePathwaysText\":\"口服\",\"drugEntrustCode\":\"1\",\"entrustmentId\":69,\"status\":1,\"producer\":\"浙江景岳堂药业\",\"producerCode\":\"63830\",\"useDaysB\":\"7\",\"drugDisplaySplicedName\":\"[甲]连翘K\",\"type\":3,\"pharmacyId\":769,\"pharmacyName\":\"颗粒剂\"},{\"drugCost\":3.23,\"drugId\":5296417,\"drugName\":\"金银花k\",\"drugSpec\":\"6g,10g,15g\",\"drugType\":3,\"drugUnit\":\"g\",\"organDrugCode\":\"63842\",\"usingRate\":\"bid\",\"organUsingRate\":\"2\",\"usingRateText\":\"2次/日\",\"useDays\":7,\"pack\":1,\"saleName\":\"[乙]金银花k\",\"salePrice\":0.323,\"useDose\":10,\"DefaultUseDose\":6,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"useTotalDose\":10,\"usePathways\":\"po\",\"organUsePathways\":\"01\",\"usePathwaysText\":\"口服\",\"drugEntrustCode\":\"1\",\"entrustmentId\":69,\"status\":1,\"producer\":\"河南\",\"producerCode\":\"63842\",\"useDaysB\":\"7\",\"drugDisplaySplicedName\":\"[甲]金银花K\",\"type\":3,\"pharmacyId\":769,\"pharmacyName\":\"颗粒剂\"}],\"introduce\":\"1.【药品名称】扶正御邪2号2.【作用类别】清热解毒3.【适应症】可清热解毒、扶正祛邪，对于流感易感人群、新冠肺炎的患者以及密接或者次密接者均适宜。4.【用法用量】每天2次，1次1包，开水冲服，儿童半量，婴幼儿1/3量。5.【禁忌】尚不明确6.【注意事项】（1）忌烟酒、辛辣、鱼腥食物。（2）糖尿病患者及有高血压、心脏病、肝病、肾病等慢性病严重者应在医师指导下用药。（3）儿童、妇女、哺乳期妇女、年老体弱应在医师指导下用药。（4）儿童必须在成人监护下使用。（5）对本品过敏者禁用，过敏体质者慎用。（6）如正在使用其他药品，使用本品前请咨询。7.【药物互相作用】如与其他药物同时使用可能会发生药物相互作用，详情请咨询医师或药师。8.【不良反应】尚不明确\",\"recipeBean\":{\"actualPrice\":12.45,\"bussSource\":2,\"checkMode\":1,\"checkStatus\":0,\"chooseFlag\":0,\"clinicId\":815423329,\"clinicOrgan\":1000017,\"copyNum\":7,\"createDate\":1649231819344,\"fromflag\":1,\"offlineRecipeName\":\"扶正御邪2号\",\"giveMode\":2,\"mpiid\":\"2c94816d7ac34526017ac72b76f00001\",\"notation\":0,\"organName\":\"浙江省中医院（湖滨院区）\",\"patientName\":\"患者一号a\",\"pushFlag\":0,\"recipeExtend\":{\"symptomId\":\"97463\",\"symptomName\":\"虚实夹杂证\"},\"recipeMode\":\"ngarihealth\",\"recipeSourceType\":1,\"recipeType\":3,\"remindFlag\":0,\"reviewType\":1,\"status\":0,\"totalMoney\":12.45},\"mouldId\":114,\"maxNum\":28,\"backgroundImg\":\"6304899cdaf658361ddf85c9\",\"title\":\"扶正御邪方2号（1帖）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"扶正御邪2号方配药\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"时行感冒\",\"code\":\"ZX10361\"}]},{\"key\":\"tcmSyndrome\",\"name\":\"中医证候\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"虚实夹杂证\",\"code\":\"ZX50066\"}]}]},{\"introduce\":\"1. 【药品名称】失眠外用方2.【作用类别】除烦解郁，养血安神3.【适应症】肝郁血虚、难寐易醒人群。4.【用法用量】外用，每天1次，1次1包，开水冲泡，待温后浸泡脚部。5.【禁忌】尚不明确6.【注意事项】（1）各种严重出血病或局部受伤在二十四小时以内的患者不宜用药。（2）恶性肿瘤、肾衰竭、心力衰竭、败血症等各种危重病患者不宜用药。（3）急性传染病、外科急症或中毒的患者不宜用药。（4）足部患开放性软组织损伤、严重感染以及较重静脉曲张者不宜用药。（5）儿童、妇女、哺乳期妇女、年老体弱应在医师指导下用药。（6）儿童必须在成人监护下使用。（7）对本品过敏者禁用，过敏体质者慎用。（8）如正在使用其他药品，使用本品前请咨询。（9）在用药过程中，由于足部血管受热扩张，可能会出现头晕等现象，若出现这类现象时，应暂停足浴，平卧休息，待症状消失后在进行足浴。7.【药物互相作用】如与其他药物同时使用可能会发生药物相互作用，详情请咨询医师或药师。8.【不良反应】尚不明确\",\"title\":\"失眠外用方（1帖）\",\"docText\":[{\"key\":\"complain\",\"name\":\"主诉\",\"useThirdField\":0,\"value\":\"失眠外用方\"},{\"key\":\"tcmDiagnosis\",\"name\":\"中医诊断\",\"useThirdField\":0,\"type\":\"multiSearch\",\"value\":[{\"name\":\"不寐病\",\"code\":\"ZX10399\"}]}],\"backgroundImg\":\"630489b9b7ba5c3b8006523e\",\"mouldId\":115,\"maxNum\":28,\"recipeBean\":{\"actualPrice\":4.56,\"clinicOrgan\":1000017,\"organName\":\"浙江省中医院（湖滨院区）\",\"recipeType\":3,\"copyNum\":1,\"totalMoney\":4.56,\"giveMode\":2,\"fromflag\":1,\"offlineRecipeName\":\"失眠外用方\",\"recipeExtend\":{\"makeMethodId\":\"42\",\"makeMethodText\":\"普煎二汁各200mL\",\"decoctionId\":\"165\",\"decoctionText\":\"无需代煎\"}},\"detailBeanList\":[{\"saleName\":\"菖蒲\",\"drugId\":5264649,\"organDrugCode\":\"69266\",\"drugName\":\"石菖蒲\",\"drugSpec\":\"5g,12gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":30,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"qn\",\"usePathways\":\"ext\",\"organUsingRate\":\"13\",\"organUsePathways\":\"06\",\"usingRateTextFromHis\":\"每晚一次\",\"usePathwaysTextFromHis\":\"外用\",\"useTotalDose\":30,\"useDays\":1,\"drugCost\":2.724,\"salePrice\":0.0908,\"drugCode\":\"69266\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69266\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"石菖蒲 5g,12gj/g\",\"type\":3},{\"saleName\":\"[甲]玫瑰花\",\"drugId\":5264422,\"organDrugCode\":\"69076\",\"drugName\":\"玫瑰花\",\"drugSpec\":\"6g,10gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":10,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"qn\",\"usePathways\":\"ext\",\"organUsingRate\":\"13\",\"organUsePathways\":\"06\",\"usingRateTextFromHis\":\"每晚一次\",\"usePathwaysTextFromHis\":\"外用\",\"useTotalDose\":10,\"useDays\":1,\"drugCost\":1.207,\"salePrice\":0.1207,\"drugCode\":\"69076\",\"status\":1,\"producer\":\"山东\",\"producerCode\":\"69076\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"玫瑰花 6g,10gj/g\",\"type\":3},{\"saleName\":\"夜交藤\",\"drugId\":5264442,\"organDrugCode\":\"69096\",\"drugName\":\"首乌藤\",\"drugSpec\":\"6g,10g,15gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":15,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"qn\",\"usePathways\":\"ext\",\"organUsingRate\":\"13\",\"organUsePathways\":\"06\",\"usingRateTextFromHis\":\"每晚一次\",\"usePathwaysTextFromHis\":\"外用\",\"useTotalDose\":15,\"useDays\":1,\"drugCost\":0.492,\"salePrice\":0.0328,\"drugCode\":\"69096\",\"status\":1,\"producer\":\"河南\",\"producerCode\":\"69096\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"首乌藤 6g,10g,15gj/g\",\"type\":3},{\"saleName\":\"[甲]合欢皮\",\"drugId\":5264763,\"organDrugCode\":\"69261\",\"drugName\":\"合欢皮\",\"drugSpec\":\"6g,9gj\",\"pack\":1,\"drugUnit\":\"g\",\"useDose\":9,\"defaultUseDose\":1,\"useDoseUnit\":\"g\",\"dosageUnit\":\"g\",\"usingRate\":\"qn\",\"usePathways\":\"ext\",\"organUsingRate\":\"13\",\"organUsePathways\":\"06\",\"usingRateTextFromHis\":\"每晚一次\",\"usePathwaysTextFromHis\":\"外用\",\"useTotalDose\":9,\"useDays\":1,\"drugCost\":0.1341,\"salePrice\":0.0149,\"drugCode\":\"69261\",\"status\":1,\"producer\":\"浙江\",\"producerCode\":\"69261\",\"useDaysB\":\"1\",\"drugType\":3,\"superScalarCode\":\"\",\"superScalarName\":\"\",\"pharmacyId\":816,\"pharmacyName\":\"协定方\",\"drugDisplaySplicedName\":\"合欢皮 6g,9gj/g\",\"type\":3}]}]");
        } else {
            jsonArray = JSON.parseArray(paramName);
        }

        for (Object value : jsonArray) {
            JSONObject jb = (JSONObject) value;
            FastRecipe fastRecipe = new FastRecipe();
            fastRecipe.setIntroduce(Objects.toString(jb.get("introduce"), ""));
            fastRecipe.setTitle(Objects.toString(jb.get("title"), ""));
            fastRecipe.setBackgroundImg(Objects.toString(jb.get("backgroundImg"), ""));
            fastRecipe.setMaxNum(Objects.isNull(jb.get("maxNum")) ? null : (int) jb.get("maxNum"));
            fastRecipe.setDocText(Objects.isNull(jb.get("docText")) ? "" : JSON.toJSONString(jb.get("docText")));
            JSONObject recipeBean = (JSONObject) jb.get("recipeBean");

            fastRecipe.setActualPrice((BigDecimal) recipeBean.get("actualPrice"));
            fastRecipe.setClinicOrgan((Integer) recipeBean.get("clinicOrgan"));
            fastRecipe.setOrganName((String) recipeBean.get("organName"));
            fastRecipe.setRecipeType((Integer) recipeBean.get("recipeType"));
            fastRecipe.setCopyNum(1);
            fastRecipe.setTotalMoney((BigDecimal) recipeBean.get("totalMoney"));
            fastRecipe.setGiveMode((Integer) recipeBean.get("giveMode"));
            fastRecipe.setFromFlag((Integer) recipeBean.get("fromflag"));
            fastRecipe.setOfflineRecipeName((String) recipeBean.get("offlineRecipeName"));
            fastRecipe.setOrderNum(0);
            fastRecipe.setNeedQuestionnaire(0);
            fastRecipe.setStatus(1);

            JSONObject recipeBeanEx = (JSONObject) recipeBean.get("recipeExtend");
            if (Objects.nonNull(recipeBeanEx)) {
                fastRecipe.setMakeMethodId((String) recipeBeanEx.get("makeMethodId"));
                fastRecipe.setMakeMethodText((String) recipeBeanEx.get("makeMethodText"));
                fastRecipe.setDecoctionId((String) recipeBeanEx.get("decoctionId"));
                fastRecipe.setDecoctionText((String) recipeBeanEx.get("decoctionText"));
            }
            FastRecipe fastRecipe1 = fastRecipeDAO.save(fastRecipe);

            JSONArray detailBeanList = (JSONArray) jb.get("detailBeanList");
            if (Objects.nonNull(detailBeanList)) {
                for (Object o : detailBeanList) {
                    FastRecipeDetail fastRecipeDetail = new FastRecipeDetail();
                    JSONObject detailBean = (JSONObject) o;
                    fastRecipeDetail.setFastRecipeId(fastRecipe1.getId());
                    fastRecipeDetail.setSaleName((String) detailBean.get("saleName"));
                    fastRecipeDetail.setDrugId((Integer) detailBean.get("drugId"));
                    fastRecipeDetail.setOrganDrugCode((String) detailBean.get("organDrugCode"));
                    fastRecipeDetail.setDrugName((String) detailBean.get("drugName"));
                    fastRecipeDetail.setDrugSpec((String) detailBean.get("drugSpec"));
                    fastRecipeDetail.setPack((Integer) detailBean.get("pack"));
                    fastRecipeDetail.setDrugUnit((String) detailBean.get("drugUnit"));
                    if (Objects.nonNull(detailBean.get("useDose"))) {
                        fastRecipeDetail.setUseDose(Double.valueOf(detailBean.get("useDose").toString()));
                    }
                    if (Objects.nonNull(detailBean.get("defaultUseDose"))) {
                        fastRecipeDetail.setDefaultUseDose(Double.valueOf(detailBean.get("defaultUseDose").toString()));
                    }
                    fastRecipeDetail.setUseDoseUnit((String) detailBean.get("useDoseUnit"));
                    fastRecipeDetail.setDosageUnit((String) detailBean.get("dosageUnit"));
                    fastRecipeDetail.setUsingRate((String) detailBean.get("usingRate"));
                    fastRecipeDetail.setUsePathways((String) detailBean.get("usePathways"));
                    fastRecipeDetail.setOrganUsingRate((String) detailBean.get("organUsingRate"));
                    fastRecipeDetail.setOrganUsePathways((String) detailBean.get("organUsePathways"));
                    fastRecipeDetail.setUsePathwaysTextFromHis((String) detailBean.get("usePathwaysTextFromHis"));
                    fastRecipeDetail.setUsingRateTextFromHis((String) detailBean.get("usingRateTextFromHis"));
                    if (Objects.nonNull(detailBean.get("useTotalDose"))) {
                        fastRecipeDetail.setUseTotalDose(Double.valueOf(detailBean.get("useTotalDose").toString()));
                    }
                    fastRecipeDetail.setUseDays((Integer) detailBean.get("useDays"));
                    fastRecipeDetail.setDrugCost((BigDecimal) detailBean.get("drugCost"));
                    fastRecipeDetail.setSalePrice((BigDecimal) detailBean.get("salePrice"));
                    fastRecipeDetail.setDrugCode((String) detailBean.get("drugCode"));
                    fastRecipeDetail.setStatus((Integer) detailBean.get("status"));
                    fastRecipeDetail.setProducer((String) detailBean.get("producer"));
                    fastRecipeDetail.setProducerCode((String) detailBean.get("producerCode"));
                    fastRecipeDetail.setUseDaysB((String) detailBean.get("useDaysB"));
                    fastRecipeDetail.setDrugType((Integer) detailBean.get("drugType"));
                    fastRecipeDetail.setSuperScalarCode((String) detailBean.get("superScalarCode"));
                    fastRecipeDetail.setSuperScalarName((String) detailBean.get("superScalarName"));
                    fastRecipeDetail.setPharmacyId((Integer) detailBean.get("pharmacyId"));
                    fastRecipeDetail.setPharmacyName((String) detailBean.get("pharmacyName"));
                    fastRecipeDetail.setDrugDisplaySplicedName((String) detailBean.get("drugDisplaySplicedName"));
                    fastRecipeDetail.setDrugDisplaySplicedSaleName((String) detailBean.get("drugDisplaySplicedSaleName"));
                    fastRecipeDetail.setType((Integer) detailBean.get("type"));
                    fastRecipeDetailDAO.save(fastRecipeDetail);
                }
            }
        }
    }

    @Override
    public Map<String, Object> findRecipeAndDetailsByRecipeIdAndOrgan(Integer recipeId, Integer organId) {
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (Objects.nonNull(recipe) && organId.equals(recipe.getClinicOrgan())) {
            return remoteRecipeService.findRecipeAndDetailsAndCheckById(recipeId);
        } else {
            throw new DAOException("无法找到该处方单");
        }
    }

}

