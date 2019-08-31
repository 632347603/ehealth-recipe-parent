package recipe.drugsenterprise;

import com.alijk.bqhospital.alijk.dto.BaseResult;
import com.alijk.bqhospital.alijk.service.AlihealthHospitalService;
import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.ngari.patient.dto.DepartmentDTO;
import com.ngari.patient.dto.DoctorDTO;
import com.ngari.patient.dto.EmploymentDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.*;
import com.ngari.recipe.drugsenterprise.model.Position;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.filedownload.service.IFileDownloadService;
import com.taobao.api.FileItem;
import com.taobao.api.request.AlibabaAlihealthRxPrescriptionAddRequest;
import com.taobao.api.request.AlibabaAlihealthRxPrescriptionGetRequest;
import com.taobao.api.request.AlibabaAlihealthRxPrescriptionImageUploadRequest;
import com.taobao.api.request.AlibabaAlihealthRxPrescriptionStatusUpdateRequest;
import com.taobao.api.response.AlibabaAlihealthRxPrescriptionAddResponse;
import com.taobao.api.response.AlibabaAlihealthRxPrescriptionGetResponse;
import com.taobao.api.response.AlibabaAlihealthRxPrescriptionImageUploadResponse;
import com.taobao.api.response.AlibabaAlihealthRxPrescriptionStatusUpdateResponse;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.utils.DateConversion;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import recipe.ApplicationUtils;
import recipe.bean.DrugEnterpriseResult;
import recipe.constant.DrugEnterpriseConstant;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeDetailDAO;
import recipe.dao.RecipeExtendDAO;
import recipe.dao.SaleDrugListDAO;
import sun.misc.BASE64Decoder;

import javax.annotation.Nullable;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 阿里大药房对接服务
 * @author yinsheng
 * @date 2019\2\28 0028 14:09
 */
@RpcBean("aldyfRemoteService")
public class AldyfRemoteService extends AccessDrugEnterpriseService{

    private static final Logger LOGGER = LoggerFactory.getLogger(AldyfRemoteService.class);

    private static final String EXPIRE_TIP = "请重新授权";

    @Autowired
    private AlihealthHospitalService alihealthHospitalService;

    @Autowired
    private RecipeExtendDAO recipeExtendDAO;

    @Autowired
    private AldyfRedisService aldyfRedisService;

    @Override
    public void tokenUpdateImpl(DrugsEnterprise drugsEnterprise) {
        LOGGER.info("AldyfRemoteService tokenUpdateImpl not implement.");
    }

    @Override
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        if (ObjectUtils.isEmpty(recipeIds)) {
            getDrugEnterpriseResult(result, "处方ID参数为空");
        }
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Integer depId = enterprise.getId();

        List<Recipe> recipeList = recipeDAO.findByRecipeIds(recipeIds);

        if (!ObjectUtils.isEmpty(recipeList)) {
            PatientService patientService = BasicAPI.getService(PatientService.class);
            DoctorService doctorService = BasicAPI.getService(DoctorService.class);
            EmploymentService employmentService = BasicAPI.getService(EmploymentService.class);
            OrganService organService = BasicAPI.getService(OrganService.class);
            DepartmentService departmentService = BasicAPI.getService(DepartmentService.class);

            for (Recipe dbRecipe : recipeList ) {
                String loginId = patientService.getLoginIdByMpiId(dbRecipe.getRequestMpiId());
                String accessToken = aldyfRedisService.getTaobaoAccessToken(loginId);
                if (ObjectUtils.isEmpty(accessToken)) {
                    return getDrugEnterpriseResult(result, EXPIRE_TIP);
                }
                alihealthHospitalService.setTopSessionKey(accessToken);
                LOGGER.info("获取到accessToken:{}, loginId:{}", accessToken, loginId);
                String organizeCode = organService.getOrganizeCodeByOrganId(dbRecipe.getClinicOrgan());
                AlibabaAlihealthRxPrescriptionAddRequest prescriptionAddRequest = new AlibabaAlihealthRxPrescriptionAddRequest();
                //获取患者信息
                AlibabaAlihealthRxPrescriptionAddRequest.PatientParam patientParam = new AlibabaAlihealthRxPrescriptionAddRequest.PatientParam();
                PatientDTO patient = patientService.get(dbRecipe.getMpiid());

                if (!ObjectUtils.isEmpty(patient)) {
                    int patientAge = patient.getBirthday() == null ? 0 : DateConversion
                            .getAge(patient.getBirthday());
                    patientParam.setPatientId(dbRecipe.getMpiid());
                    patientParam.setPatientName(patient.getPatientName());
                    patientParam.setSex("1".equals(patient.getPatientSex())?"M":"F");
                    patientParam.setAge(Long.parseLong(patientAge+""));
                    patientParam.setPhone(patient.getMobile());
                    patientParam.setAddress(patient.getAddress());
                    patientParam.setIdNumber(patient.getIdcard());
                } else {
                    return getDrugEnterpriseResult(result, "患者不存在");
                }
                prescriptionAddRequest.setPatientParam(patientParam);

                //获取医生信息
                DoctorDTO doctor = doctorService.get(dbRecipe.getDoctor());

                AlibabaAlihealthRxPrescriptionAddRequest.DoctorParam doctorParam = new AlibabaAlihealthRxPrescriptionAddRequest.DoctorParam();

                String organCode = organService.getOrganizeCodeByOrganId(dbRecipe.getClinicOrgan());
                if (StringUtils.isNotEmpty(organCode)) {
                    doctorParam.setHospitalId(organCode);
                    doctorParam.setHospitalName(dbRecipe.getOrganName());
                } else {
                    return getDrugEnterpriseResult(result, "机构不存在");
                }

                if (!ObjectUtils.isEmpty(doctor)) {
                    doctorParam.setDoctorName(doctor.getName());
                    EmploymentDTO employment = employmentService.getPrimaryEmpByDoctorId(dbRecipe.getDoctor());
                    if (!ObjectUtils.isEmpty(employment)) {
                        Integer departmentId = employment.getDepartment();
                        DepartmentDTO departmentDTO = departmentService.get(departmentId);
                        doctorParam.setQualificationNumber(doctor.getDoctorCertCode());
                        if (!ObjectUtils.isEmpty(departmentDTO)) {
                            doctorParam.setDeptName(StringUtils.isEmpty(departmentDTO.getName())?"全科":departmentDTO.getName());
                        } else {
                            return getDrugEnterpriseResult(result, "医生主执业点不存在");
                        }

                    } else {
                        return getDrugEnterpriseResult(result, "医生主执业点不存在");
                    }
                } else {
                    return getDrugEnterpriseResult(result, "医生不存在");
                }
                prescriptionAddRequest.setDoctorParam(doctorParam);
                String ossId = dbRecipe.getSignImg();
                String ossKey ;
                try{
                    IFileDownloadService fileDownloadService = ApplicationUtils.getBaseService(IFileDownloadService.class);
                    String b = fileDownloadService.downloadImg(ossId);
                    byte[] img ;
                    if (ObjectUtils.isEmpty(b)) {
                        return getDrugEnterpriseResult(result, "处方图片获取失败");
                    } else {
                        img = GenerateImage(b);
                    }
                    if (img != null) {
                        AlibabaAlihealthRxPrescriptionImageUploadRequest imageUploadRequest = new AlibabaAlihealthRxPrescriptionImageUploadRequest();
                        imageUploadRequest.setImage(new FileItem("处方图片", img));
                        BaseResult<AlibabaAlihealthRxPrescriptionImageUploadResponse> baseResult = alihealthHospitalService.uploadPrescriptionImg(imageUploadRequest);
                        if (StringUtils.isEmpty(baseResult.getErrCode())) {
                            //上传成功
                            ossKey = baseResult.getData().getOssKey();
                        } else {
                            LOGGER.info("处方图片上传失败,{},{}.", dbRecipe.getRecipeId() ,baseResult.getData().getMsg());
                            return getDrugEnterpriseResult(result, "处方图片上传失败");
                        }
                    } else {
                        LOGGER.info("获取图片失败,{}.", dbRecipe.getRecipeId());
                        return getDrugEnterpriseResult(result, "获取图片失败");
                    }
                } catch (Exception e) {
                    LOGGER.info("处方图片上传失败.{}", dbRecipe.getRecipeId(), e);
                    return getDrugEnterpriseResult(result, "处方图片上传失败");
                }
                //获取处方信息
                AlibabaAlihealthRxPrescriptionAddRequest.PrescriptionParam prescriptionParam = new AlibabaAlihealthRxPrescriptionAddRequest.PrescriptionParam();
                prescriptionParam.setRxId(dbRecipe.getRecipeCode());
                //处方状态 传固定10
                prescriptionParam.setStatus(10L);
                prescriptionParam.setOssKey(ossKey);
                prescriptionParam.setOutHospitalId(organizeCode);
                prescriptionParam.setCreateTime(dbRecipe.getSignDate());
                LOGGER.info("prescriptionParam 处方信息:{}.", getJsonLog(prescriptionParam));
                prescriptionAddRequest.setPrescriptionParam(prescriptionParam);

                RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(dbRecipe.getRecipeId());
                //DiagnosticParam 患者主诉
                AlibabaAlihealthRxPrescriptionAddRequest.DiagnosticParam diagnosticParam = new AlibabaAlihealthRxPrescriptionAddRequest.DiagnosticParam();
                diagnosticParam.setComplaints(dbRecipe.getMemo());               //诊断
                diagnosticParam.setDiagnosis(recipeExtend.getHistoryOfPresentIllness());   //患者主诉
                diagnosticParam.setDisease(dbRecipe.getOrganDiseaseName());      //疾病
                LOGGER.info("DiagnosticParam 患者主诉:{}.", getJsonLog(diagnosticParam));
                prescriptionAddRequest.setDiagnosticParam(diagnosticParam);

                //DrugParam药品详情
                AlibabaAlihealthRxPrescriptionAddRequest.DrugParam drugParam;
                RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
                List<Recipedetail> detailList = detailDAO.findByRecipeId(dbRecipe.getRecipeId());
                List<AlibabaAlihealthRxPrescriptionAddRequest.DrugParam> drugParams = new ArrayList<>();
                if (!ObjectUtils.isEmpty(detailList)) {
                    SaleDrugListDAO saleDrugDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
                    for (int i = 0; i < detailList.size(); i++) {
                        //一张处方单可能包含相同的药品
                        SaleDrugList saleDrugList = saleDrugDAO.getByDrugIdAndOrganId(detailList.get(i).getDrugId(), depId);
                        if (ObjectUtils.isEmpty(saleDrugList)) {
                            return getDrugEnterpriseResult(result, "未找到对应的saleDrugList");
                        }
                        drugParam = new AlibabaAlihealthRxPrescriptionAddRequest.DrugParam();
                        drugParam.setDrugId(saleDrugList.getOrganDrugCode());
                        drugParam.setCommonName(saleDrugList.getDrugName());  //药品通用名
                        drugParam.setSpec(saleDrugList.getDrugSpec());        //药品规格
                        drugParam.setDoseUnit(detailList.get(i).getDrugUnit());      //开具单位(盒)
                        drugParam.setDrugName(saleDrugList.getSaleName());    //商品名称
                        try {
                            drugParam.setDoseUsageAdvice(DictionaryController.instance().get("eh.cdr.dictionary.UsingRate").getText(detailList.get(i).getUsingRate()));
                            drugParam.setDoseUsage(DictionaryController.instance().get("eh.cdr.dictionary.UsePathways").getText(detailList.get(i).getUsePathways()));
                        } catch (ControllerException e) {
                            return getDrugEnterpriseResult(result, "药物使用频率使用途径获取失败");
                        }
                        drugParam.setNum((new Double(detailList.get(i).getUseTotalDose())).longValue());  //药品数量

                        drugParams.add(drugParam);
                        prescriptionAddRequest.setDrugList(drugParams);
                    }
                }
                try{
                    LOGGER.info("doctorParam:[{}],patientParam:[{}],diagnosticParam:[{}],drugParams:[{}],prescriptionParam:[{}]",getJsonLog(doctorParam),getJsonLog(patientParam),getJsonLog(diagnosticParam),getJsonLog(drugParams),getJsonLog(prescriptionParam));
                    BaseResult<AlibabaAlihealthRxPrescriptionAddResponse> baseResult = alihealthHospitalService.uploadPrescription(doctorParam, patientParam, diagnosticParam, drugParams, prescriptionParam);
                    LOGGER.info("上传处方，{}", getJsonLog(baseResult));
                    if (StringUtils.isEmpty(baseResult.getData().getErrorCode())) {
                        //说明成功,更新处方标志
                        recipeDAO.updateRecipeInfoByRecipeId(dbRecipe.getRecipeId(), ImmutableMap.of("pushFlag", 1));
                    } else {
                        return getDrugEnterpriseResult(result, baseResult.getData().getMsg());
                    }
                }catch (Exception e){
                    LOGGER.info("推送处方失败{}", dbRecipe.getRecipeId(), e);
                }
            }
        }

        return result;
    }

    @Override
    public DrugEnterpriseResult scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult syncEnterpriseDrug(DrugsEnterprise drugsEnterprise, List<Integer> drugIdList) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult pushCheckResult(Integer recipeId, Integer checkFlag, DrugsEnterprise enterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise) {
        return null;
    }

    @Override
    public String getDrugEnterpriseCallSys() {
        return DrugEnterpriseConstant.COMPANY_ALDYF;
    }

    /**
     * 推送药企处方状态，由于只是个别药企需要实现，故有默认实现
     * @param rxId  recipeCode
     * @return   DrugEnterpriseResult
     */
    @RpcService
    @Override
    public DrugEnterpriseResult updatePrescriptionStatus(String rxId, int status) {
        LOGGER.info("更新处方状态");
        PatientService patientService = BasicAPI.getService(PatientService.class);
        OrganService organService = BasicAPI.getService(OrganService.class);
        DrugEnterpriseResult drugEnterpriseResult = new DrugEnterpriseResult(DrugEnterpriseResult.SUCCESS);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe dbRecipe = recipeDAO.getByRecipeCode(rxId);
        if (ObjectUtils.isEmpty(dbRecipe)) {
            LOGGER.info("处方不存在{}.", rxId);
            return getDrugEnterpriseResult(drugEnterpriseResult, "处方不存在");
        }
        //外部医院编码
        String outHospitalId = organService.getOrganizeCodeByOrganId(dbRecipe.getClinicOrgan());
        String loginId = patientService.getLoginIdByMpiId(dbRecipe.getRequestMpiId());
        String accessToken = aldyfRedisService.getTaobaoAccessToken(loginId);
        if (ObjectUtils.isEmpty(accessToken)) {
            return getDrugEnterpriseResult(drugEnterpriseResult, EXPIRE_TIP);
        }
        LOGGER.info("获取到accessToken:{}, loginId:{}", accessToken, loginId);
        alihealthHospitalService.setTopSessionKey(accessToken);
        //调用扁鹊接口改变处方状态
        AlibabaAlihealthRxPrescriptionStatusUpdateRequest prescriptionStatusUpdateRequest = new AlibabaAlihealthRxPrescriptionStatusUpdateRequest();
        prescriptionStatusUpdateRequest.setOutHospitalId(outHospitalId);
        prescriptionStatusUpdateRequest.setRxId(rxId);
        prescriptionStatusUpdateRequest.setStatus(Long.parseLong(status+""));
        BaseResult<AlibabaAlihealthRxPrescriptionStatusUpdateResponse> baseResult = alihealthHospitalService.queryPrescription(prescriptionStatusUpdateRequest);
        LOGGER.info("更新处方状态，{}", getJsonLog(baseResult));
        getAldyfResult(drugEnterpriseResult, baseResult);
        return drugEnterpriseResult;
    }

    /**
     *
     * @param rxId  处⽅Id
     * @param queryOrder  是否查询订单
     * @return 处方单
     */
    @Override
    public DrugEnterpriseResult queryPrescription(String rxId, Boolean queryOrder) {
        PatientService patientService = BasicAPI.getService(PatientService.class);
        OrganService organService = BasicAPI.getService(OrganService.class);
        DrugEnterpriseResult drugEnterpriseResult = new DrugEnterpriseResult(DrugEnterpriseResult.SUCCESS);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe dbRecipe = recipeDAO.getByRecipeCode(rxId);
        if (ObjectUtils.isEmpty(dbRecipe)) {
            return getDrugEnterpriseResult(drugEnterpriseResult, "处方不存在");
        }
        String outHospitalId = organService.getOrganizeCodeByOrganId(dbRecipe.getClinicOrgan());
        if (StringUtils.isEmpty(outHospitalId)) {
            return getDrugEnterpriseResult(drugEnterpriseResult, "医院的外部编码不能为空");
        }
        String loginId = patientService.getLoginIdByMpiId(dbRecipe.getRequestMpiId());
        String accessToken = aldyfRedisService.getTaobaoAccessToken(loginId);
        if (ObjectUtils.isEmpty(accessToken)) {
            return getDrugEnterpriseResult(drugEnterpriseResult, EXPIRE_TIP);
        }
        LOGGER.info("获取到accessToken:{}, loginId:{},{},{}", accessToken, loginId, rxId, outHospitalId);
        alihealthHospitalService.setTopSessionKey(accessToken);
        AlibabaAlihealthRxPrescriptionGetRequest prescriptionGetRequest = new AlibabaAlihealthRxPrescriptionGetRequest();
        prescriptionGetRequest.setRxId(rxId);
        prescriptionGetRequest.setOutHospitalId(outHospitalId);
        BaseResult<AlibabaAlihealthRxPrescriptionGetResponse> responseBaseResult = alihealthHospitalService.queryPrescription(prescriptionGetRequest);
        LOGGER.info("查询处方，{}", getJsonLog(responseBaseResult));
        getAldyfResult(drugEnterpriseResult, responseBaseResult);
        return drugEnterpriseResult;
    }

    private void getAldyfResult(DrugEnterpriseResult drugEnterpriseResult, BaseResult baseResult) {
        if (StringUtils.isEmpty(baseResult.getData().getErrorCode())) {
            //说明成功
            drugEnterpriseResult.setObject(baseResult.getData());
            drugEnterpriseResult.setCode(DrugEnterpriseResult.SUCCESS);
        } else {
            String errorMsg = baseResult.getData().getMsg();
            if ("53".equals(baseResult.getData().getErrorCode())) {
                errorMsg = EXPIRE_TIP;
            }
            drugEnterpriseResult.setMsg(errorMsg);
            drugEnterpriseResult.setCode(DrugEnterpriseResult.FAIL);
        }
    }

    /**
     * 返回调用信息
     * @param result DrugEnterpriseResult
     * @param msg     提示信息
     * @return DrugEnterpriseResult
     */
    private DrugEnterpriseResult getDrugEnterpriseResult(DrugEnterpriseResult result, String msg) {
        result.setMsg(msg);
        result.setCode(DrugEnterpriseResult.FAIL);
        LOGGER.info("AldyfRemoteService-getDrugEnterpriseResult提示信息：{}.", msg);
        return result;
    }

    //base64字符串转化成图片
    private static byte[] GenerateImage(String imgStr){
        //对字节数组字符串进行Base64解码并生成图片
        if (StringUtils.isEmpty(imgStr)) //图像数据为空
            return null;
        BASE64Decoder decoder = new BASE64Decoder();
        try{
            //Base64解码
            byte[] b = decoder.decodeBuffer(imgStr);
            for(int i = 0; i < b.length; ++i){
                if(b[i]<0){
                    //调整异常数据
                    b[i]+=256;
                }
            }
            return b;
        }catch (Exception e){
            return null;
        }
    }

    private static String getJsonLog(Object object) {
        return JSONUtils.toString(object);
    }
}
