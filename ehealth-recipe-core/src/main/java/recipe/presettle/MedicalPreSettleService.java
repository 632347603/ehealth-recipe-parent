package recipe.presettle;

import com.alibaba.fastjson.JSONArray;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonArray;
import com.ngari.base.BaseAPI;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.MedicalPreSettleReqNTO;
import com.ngari.his.recipe.mode.RecipeMedicalPreSettleInfo;
import com.ngari.his.recipe.mode.RecipePreSettleDrugFeeDTO;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.HealthCardService;
import com.ngari.patient.service.OrganService;
import com.ngari.patient.service.PatientService;
import com.ngari.recipe.entity.*;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.ApplicationUtils;
import recipe.dao.*;
import recipe.enumerate.status.YesOrNoEnum;
import recipe.enumerate.type.ForceCashTypeEnum;
import recipe.hisservice.RecipeToHisService;
import recipe.manager.RecipeDetailManager;
import recipe.manager.RecipeManager;
import recipe.service.RecipeLogService;
import recipe.service.RecipeOrderService;
import recipe.util.MapValueUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * created by shiyuping on 2020/11/27
 * 医保预结算
 *
 * @author shiyuping
 */
@Service
public class MedicalPreSettleService implements IRecipePreSettleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(MedicalPreSettleService.class);

    @Autowired
    private RecipeManager recipeManager;

    @Override
    public Map<String, Object> recipePreSettle(Integer recipeId, Map<String, Object> extInfo) {
        LOGGER.info("MedicalPreSettleService.recipePreSettle req recipeId={} extInfo={}", recipeId, JSONArray.toJSONString(extInfo));
        Map<String, Object> result = Maps.newHashMap();
        result.put("code", "-1");
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeDetailManager recipeDetailManager = AppContextHolder.getBean("recipeDetailManager", RecipeDetailManager.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (recipe == null) {
            result.put("msg", "查不到该处方");
            return result;
        }
        try {
            MedicalPreSettleReqNTO request = new MedicalPreSettleReqNTO();
            request.setClinicId(String.valueOf(recipe.getClinicId()));
            request.setClinicOrgan(recipe.getClinicOrgan());
            request.setRecipeId(String.valueOf(recipeId));
            request.setHisRecipeNo(recipe.getRecipeCode());
            String recipeCodeS = MapValueUtil.getString(extInfo, "recipeNoS");
            if (recipeCodeS != null) {
                request.setHisRecipeNoS(JSONUtils.parse(recipeCodeS, ArrayList.class));
            }
            request.setDoctorId(recipe.getDoctor() + "");
            request.setDoctorName(recipe.getDoctorName());
            request.setDepartId(recipe.getDepart() + "");
            //参保地区行政区划代码
            request.setInsuredArea(MapValueUtil.getString(extInfo, "insuredArea"));
            IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
            //获取医保支付流程配置（2-原省医保 3-长三角）
            Integer insuredAreaType = (Integer) configService.getConfiguration(recipe.getClinicOrgan(), "provincialMedicalPayFlag");
            if (new Integer(3).equals(insuredAreaType)) {
                if (StringUtils.isEmpty(request.getInsuredArea())) {
                    result.put("msg", "参保地区行政区划代码为空,无法进行预结算");
                    return result;
                }
                //省医保参保类型 1 长三角 没有赋值就是原来的省直医保
                request.setInsuredAreaType("1");
                //结算的时候会用到
                recipeExtendDAO.updateRecipeExInfoByRecipeId(recipe.getRecipeId(), ImmutableMap.of("insuredArea", request.getInsuredArea()));
            }
            RecipeExtend ext = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
            if (ext != null) {
                // 大病标识
                if (StringUtils.isNotEmpty(ext.getIllnessType())) {
                    request.setIllnessType(ext.getIllnessType());
                }
                if (StringUtils.isNotEmpty(ext.getRegisterID())) {
                    request.setRegisterID(ext.getRegisterID());
                }
                //默认是医保，医生选择了自费时，强制设置为自费
                if ((ext.getMedicalType() != null && "0".equals(ext.getMedicalType())) ||
                        (null != ext.getForceCashType() && ForceCashTypeEnum.FORCE_CASH_TYPE.getType().equals(ext.getForceCashType()))) {
                    request.setIszfjs("1");
                } else {
                    request.setIszfjs("0");
                }
            }
            try {
                request.setDepartName(DictionaryController.instance().get("eh.base.dictionary.Depart").getText(recipe.getDepart()));
            } catch (ControllerException e) {
                LOGGER.warn("MedicalPreSettleService 字典转化异常");
            }
            //患者信息
            PatientService patientService = BasicAPI.getService(PatientService.class);
            PatientDTO patientBean = patientService.get(recipe.getMpiid());
            request.setPatientId(recipe.getPatientID());
            request.setPatientName(patientBean.getPatientName());
            request.setIdcard(patientBean.getIdcard());
            request.setCertificate(patientBean.getCertificate());
            request.setCertificateType(patientBean.getCertificateType());
            request.setBirthday(patientBean.getBirthday());
            request.setAddress(patientBean.getAddress());
            request.setMobile(patientBean.getMobile());
            request.setGuardianName(patientBean.getGuardianName());
            request.setGuardianTel(patientBean.getLinkTel());
            request.setGuardianCertificate(patientBean.getGuardianCertificate());
            String recipeCostNumber = MapValueUtil.getString(extInfo, "recipeCostNumber");
            if (StringUtils.isNotEmpty(recipeCostNumber)) {
                request.setRecipeCostNumber(JSONUtils.parse(recipeCostNumber, ArrayList.class));
            }

            DrugsEnterpriseDAO drugEnterpriseDao = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
            Integer depId = MapValueUtil.getInteger(extInfo, "depId");
            //获取杭州市市民卡
            if (depId != null) {
                DrugsEnterprise drugEnterprise = drugEnterpriseDao.get(depId);
                if (drugEnterprise != null) {
                    HealthCardService healthCardService = ApplicationUtils.getBasicService(HealthCardService.class);
                    //杭州市互联网医院监管中心 管理单元eh3301
                    OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
                    OrganDTO organDTO = organService.getByManageUnit("eh3301");
                    String bxh = null;
                    if (organDTO != null) {
                        bxh = healthCardService.getMedicareCardId(recipe.getMpiid(), organDTO.getOrganId());
                    }
                    request.setBxh(bxh);
                }
            }

            RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
            try {
                if (Objects.nonNull(recipeOrder)) {
                    request.setRegisterFee(recipeOrder.getRegisterFee());
                    request.setRegisterFeeNo(recipeOrder.getRegisterFeeNo());
                    request.setTcmFee(recipeOrder.getTcmFee());
                    request.setTcmFeeNo(recipeOrder.getTcmFeeNo());
                    request.setOrderCode(recipeOrder.getOrderCode());
                }
            } catch (Exception e) {
                LOGGER.error("MedicalPreSettleService 代缴费用有误");
            }

            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            LOGGER.info("MedicalPreSettleService recipeId={} req={}", recipeId, JSONUtils.toString(request));
            HisResponseTO<RecipeMedicalPreSettleInfo> hisResult = service.recipeMedicalPreSettleN(request);
            LOGGER.info("MedicalPreSettleService recipeId={} res={}", recipeId, JSONUtils.toString(hisResult));
            if (hisResult != null && "200".equals(hisResult.getMsgCode())) {
                if (hisResult.getData() != null) {
                    //自费金额
                    String cashAmount = hisResult.getData().getZfje();
                    //医保支付金额
                    String fundAmount = hisResult.getData().getYbzf();
                    //总金额
                    String totalAmount = hisResult.getData().getZje();
                    if (ext != null) {
                        Map<String, String> map = Maps.newHashMap();
                        //杭州互联网用到registerNo、hisSettlementNo，支付的时候需要回写
                        //不知道registerNo有什么用
                        map.put("registerNo", hisResult.getData().getGhxh());
                        map.put("hisSettlementNo", hisResult.getData().getSjh());
                        //平台和杭州互联网都用到
                        map.put("preSettleTotalAmount", totalAmount);
                        map.put("fundAmount", fundAmount);
                        map.put("cashAmount", cashAmount);
                        //此时订单已经生成还需要更新订单信息
                        if (recipeOrder != null) {
                            RecipeOrderService recipeOrderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);
                            if (!recipeOrderService.dealWithOrderInfo(map, recipeOrder, recipe)) {
                                result.put("msg", "预结算更新订单信息失败");
                                return result;
                            }
                        }
                    }
                    List recipeIds = MapValueUtil.getList(extInfo,"recipeIds");
                    recipeDetailManager.saveRecipePreSettleDrugFeeDTOS(hisResult.getData().getRecipePreSettleDrugFeeDTOS(), recipeIds);

                    result.put("totalAmount", totalAmount);
                    result.put("fundAmount", fundAmount);
                    result.put("cashAmount", cashAmount);
                    //把hisId保存到处方扩展表
                    try {
                        recipeManager.saveRecipeExtendChargeId(recipeCodeS,recipe.getClinicOrgan(),hisResult.getData().getCodeMap());
                    }catch (Exception e){
                        LOGGER.error("MedicalPreSettleService saveRecipeExtendChargeId error", e);
                    }
                }
                result.put("code", "200");
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "处方预结算成功");
            } else {
                String msg;
                if (hisResult != null) {
                    msg = "his返回:" + hisResult.getMsg();
                } else {
                    msg = "前置机未实现预结算接口";
                }
                result.put("msg", msg);
                //日志记录
                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "处方预结算失败-原因:" + msg);
            }
        } catch (Exception e) {
            LOGGER.error("MedicalPreSettleService recipeId={} error", recipeId, e);
            throw new DAOException(609, "处方预结算异常");
        }
        return result;
    }
}
