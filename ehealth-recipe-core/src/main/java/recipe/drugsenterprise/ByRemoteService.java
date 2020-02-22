package recipe.drugsenterprise;

import com.ngari.patient.dto.DepartmentDTO;
import com.ngari.patient.dto.DoctorDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.*;
import com.ngari.recipe.drugsenterprise.model.DepDetailBean;
import com.ngari.recipe.drugsenterprise.model.Position;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.hisprescription.model.HospitalRecipeDTO;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ApplicationUtils;
import recipe.bean.DrugEnterpriseResult;
import recipe.constant.DrugEnterpriseConstant;
import recipe.dao.*;
import recipe.drugsenterprise.bean.*;
import recipe.drugsenterprise.bean.yd.httpclient.HttpsClientUtils;
import recipe.service.common.RecipeCacheService;
import recipe.util.DateConversion;
import recipe.util.MapValueUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

/**
* @Description: ByRemoteService 类（或接口）是 对接上海六院易复诊药企服务接口
* @Author: HDC
* @Date: 2020/2/19
*/

@RpcBean("byRemoteService")
public class ByRemoteService extends AccessDrugEnterpriseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ByRemoteService.class);

    private String ORGANIZATION;
    //HttpUrl
    private static final String httpUrl = "";
    //开处方
    private static final String addHospitalPrescriptionHttpUrl = "shanghaisix-api/prescription/addHospitalPrescription";
    //同步药品接口
    private static final String correspondingHospDrugHttpUrl = "shanghaisix-api/systemCorresponding/correspondingHospDrug";
    //查询处方药品库存接口
    private static final String checkPrescriptionDrugStockHttpUrl = "shanghaisix-api/prescription/checkPrescriptionDrugStock";

    private static final String projectCode = "SHSDLRMYY";

    private static final String encryptKey = "EEB00CBDEA0241F7A30C8057D1D89165";

    private static final String requestHeadJsonValue = "application/json";

    private static String RESULT_SUCCESS = "200";

    public ByRemoteService() {
        RecipeCacheService recipeService = ApplicationUtils.getRecipeService(RecipeCacheService.class);
        ORGANIZATION = recipeService.getRecipeParam("organization", "");
    }
//    /**
//     * @method  corresPondingHospDrug
//     * @description 发送http请求同步药品信息,并更新
//     * @date: 2020/02/20
//     * @author: JRK
//     * @param getHospDrugDto
//     * @param request 易复诊请求对象
//     * @param httpclient http请求服务
//     * @return void
//     */
//    @RpcService
//    public DrugEnterpriseResult corresPondingHospDrug(YfzCorressPonHospDrugDto getHospDrugDto)throws IOException {
//        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
//
//        LOGGER.info("ByRemoteService.corresPondingHospDrug:[{}][{}]获得新的处方药品信息", "id", "name");
//        //发送请求，获得推送的结果
//        CloseableHttpClient httpClient = HttpClients.createDefault();
//        try {
////            if (enterprise.getBusinessUrl().contains("http:")) {
////                corresPondingHospDrugHttpRequest(result,getHospDrugDto);
////            }
//        } catch (Exception e) {
//            e.printStackTrace();
//            LOGGER.error("ByRemoteService.corresPondingHospDrug:[{}][{}]同步药品异常：{}","id", "name", e.getMessage());
//        } finally {
//            try {
//                httpClient.close();
//            } catch (Exception e) {
//                e.printStackTrace();
//                LOGGER.error("ByRemoteService.corresPondingHospDrug:http请求资源关闭异常: {}！", e.getMessage());
//            }
//        }
//        return result;
//    }
    /**
     * @method  corresPondingHospDrugByOrganIdHttpRequest
     * @description 同步药品http请求
     * @date: 2019/7/10
     * @author: JRK
     * @param organId
     * @param getHospDrugDto 同步药品信息
     * @param httpClient 请求服务
     * @return void
     */
    @RpcService
    public DrugEnterpriseResult corresPondingHospDrugByOrganIdHttpRequest(Integer organId) throws IOException {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise enterprise = drugsEnterpriseDAO.getByAccount("by");
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);

        List<OrganDrugList> orgDrugList=organDrugListDAO.findOrganDrugByOrganId(organId);
        YfzCorressPonHospDrugDto getHospDrugDto=new YfzCorressPonHospDrugDto();
        if(null != enterprise){
            if (CollectionUtils.isNotEmpty(orgDrugList)) {
                getHospDrugDto.setAccess_token(enterprise.getToken());
                List<YfzHospDrugDto> hospDrugList=new ArrayList<YfzHospDrugDto>();
                for (OrganDrugList organDrug : orgDrugList) {
                    YfzHospDrugDto dto=new YfzHospDrugDto();
                    dto.setHospDrugId(organDrug.getDrugId().toString());
                    dto.setHospDrugPrice(String.valueOf(organDrug.getSalePrice()));
                    dto.setHospDrugGenericName(organDrug.getDrugName());
                    dto.setHospDrugTradeName(organDrug.getSaleName());
                    dto.setHospDrugSpec(organDrug.getDrugSpec());
                    dto.setHospDrugCompanyName(organDrug.getProducer());
                    dto.setHospDrugApproveNumber(organDrug.getLicenseNumber());
                    hospDrugList.add(dto);
                }
                getHospDrugDto.setHospDrugList(hospDrugList);
                //发送请求，获得推送的结果
                CloseableHttpClient httpClient = HttpClients.createDefault();
                try {
                    YfzEncryptDto encryptDto=new YfzEncryptDto();
                    encryptDto.setKey(enterprise.getToken());
                    encryptDto.setOriginaldata("projectCode="+projectCode+"&timespan="+ DateConversion.formatDateTime(DateConversion.getFormatDate(new Date(),DateConversion.PRESCRITION_DATE_TIME)));
                    String originaldata1=encrypt(encryptDto,enterprise);
                    encryptDto.setOriginaldata(JSONUtils.toString(getHospDrugDto));
                    String originaldata2=encrypt(encryptDto,enterprise);
                    String requestStr = JSONUtils.toString(originaldata2);
                    Map<String,String> extendHeaders=new HashMap<String,String>();
                    extendHeaders.put("Content-Type",requestHeadJsonValue);
                    extendHeaders.put("projectCode",projectCode);
                    extendHeaders.put("encryptData",originaldata1);

                    LOGGER.info("ByRemoteService.corresPondingHospDrug:[{}][{}]同步药品请求，请求内容：{}", "id", "name", requestStr);
                    String outputData = HttpsClientUtils.doPost(httpUrl+correspondingHospDrugHttpUrl, requestStr,extendHeaders);
                    //获取响应消息
                    LOGGER.info("ByRemoteService.corresPondingHospDrug:[{}][{}]同步药品请求，获取响应消息：{}", "id", "name", JSONUtils.toString(outputData));
                    YfzDecryptDto decryptDto=new YfzDecryptDto();
                    decryptDto.setKey(enterprise.getToken());
                    decryptDto.setEncryptdata(outputData);
                    Map resultMap = JSONUtils.parse(decrypt(decryptDto,enterprise), Map.class);
                    int resCode = MapValueUtil.getInteger(resultMap, "code");
                    String message = MapValueUtil.getString(resultMap, "message");
                    String responseData = MapValueUtil.getString(resultMap, "responseData");
                    if (RESULT_SUCCESS.equals(resCode)) {
                        result.setCode(resCode);
                        result.setMsg(message + responseData);
                    }else{
                        result.setCode(resCode);
                        getFailResult(result, message + responseData);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.error("ByRemoteService.syncEnterpriseDrug:[{}][{}]同步药品异常：{}",enterprise.getId(), enterprise.getName(), e.getMessage());
                } finally {
                    try {
                        httpClient.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        LOGGER.error("ByRemoteService.syncEnterpriseDrug:http请求资源关闭异常: {}！", e.getMessage());
                    }
                }

            }else {
                LOGGER.warn("syncDrugTask 药企[{}]无可同步药品.", enterprise.getName());
            }
        }
        return result;
    }
    /**
     * @method  corresPondingHospDrugByOrganDrugListHttpRequest
     * @description 同步药品http请求
     * @date: 2019/7/10
     * @author: JRK
     * @param organDrug
     * @param getHospDrugDto 同步药品信息
     * @param httpClient 请求服务
     * @return void
     */
    @RpcService
    public DrugEnterpriseResult corresPondingHospDrugByOrganDrugListHttpRequest(OrganDrugList organDrug) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise enterprise = drugsEnterpriseDAO.getByAccount("by");
        YfzCorressPonHospDrugDto getHospDrugDto=new YfzCorressPonHospDrugDto();
        if(null != enterprise){
            List<YfzHospDrugDto> hospDrugList=new ArrayList<YfzHospDrugDto>();
                getHospDrugDto.setAccess_token(enterprise.getToken());
                YfzHospDrugDto dto=new YfzHospDrugDto();
                dto.setHospDrugId(organDrug.getDrugId().toString());
                dto.setHospDrugPrice(String.valueOf(organDrug.getSalePrice()));
                dto.setHospDrugGenericName(organDrug.getDrugName());
                dto.setHospDrugTradeName(organDrug.getSaleName());
                dto.setHospDrugSpec(organDrug.getDrugSpec());
                dto.setHospDrugCompanyName(organDrug.getProducer());
                dto.setHospDrugApproveNumber(organDrug.getLicenseNumber());
                hospDrugList.add(dto);
                getHospDrugDto.setHospDrugList(hospDrugList);
                //发送请求，获得推送的结果
                CloseableHttpClient httpClient = HttpClients.createDefault();
                try {
                    YfzEncryptDto encryptDto=new YfzEncryptDto();
                    encryptDto.setKey(enterprise.getToken());
                    encryptDto.setOriginaldata("projectCode="+projectCode+"&timespan="+ DateConversion.formatDateTime(DateConversion.getFormatDate(new Date(),DateConversion.PRESCRITION_DATE_TIME)));
                    String originaldata1=encrypt(encryptDto,enterprise);
                    encryptDto.setOriginaldata(JSONUtils.toString(getHospDrugDto));
                    String originaldata2=encrypt(encryptDto,enterprise);
                    String requestStr = JSONUtils.toString(originaldata2);
                    Map<String,String> extendHeaders=new HashMap<String,String>();
                    extendHeaders.put("Content-Type",requestHeadJsonValue);
                    extendHeaders.put("projectCode",projectCode);
                    extendHeaders.put("encryptData",originaldata1);

                    LOGGER.info("ByRemoteService.corresPondingHospDrug:[{}][{}]同步药品请求，请求内容：{}", "id", "name", requestStr);
                    String outputData = HttpsClientUtils.doPost(httpUrl+correspondingHospDrugHttpUrl, requestStr,extendHeaders);
                    //获取响应消息
                    LOGGER.info("ByRemoteService.corresPondingHospDrug:[{}][{}]同步药品请求，获取响应消息：{}", "id", "name", JSONUtils.toString(outputData));
                    YfzDecryptDto decryptDto=new YfzDecryptDto();
                    decryptDto.setKey(enterprise.getToken());
                    decryptDto.setEncryptdata(outputData);
                    Map resultMap = JSONUtils.parse(decrypt(decryptDto,enterprise), Map.class);
                    int resCode = MapValueUtil.getInteger(resultMap, "code");
                    String message = MapValueUtil.getString(resultMap, "message");
                    String responseData = MapValueUtil.getString(resultMap, "responseData");
                    if (RESULT_SUCCESS.equals(resCode)) {
                        result.setCode(resCode);
                        result.setMsg(message + responseData);
                    }else{
                        result.setCode(resCode);
                        getFailResult(result, message + responseData);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.error("ByRemoteService.syncEnterpriseDrug:[{}][{}]同步药品异常：{}",enterprise.getId(), enterprise.getName(), e.getMessage());
                } finally {
                    try {
                        httpClient.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                        LOGGER.error("ByRemoteService.syncEnterpriseDrug:http请求资源关闭异常: {}！", e.getMessage());
                    }
                }
        }
        return result;
    }
    /**
     * @method  checkPrescriptionDrugStockHttpRequest
     * @description 查询处方库存http请求
     * @date: 2019/7/10
     * @author: JRK
     * @param yfzAddHospitalPrescriptionDto 药企
     * @param getHospDrugDto 查询处方库存
     * @param httpClient 请求服务
     * @return void
     */
    public DrugEnterpriseResult checkPrescriptionDrugStockHttpRequest(DrugEnterpriseResult result,DrugsEnterprise drugsEnterprise, YfzCheckPrescriptionDrugStockDto yfzAddHospitalPrescriptionDto) throws IOException {
        YfzEncryptDto encryptDto=new YfzEncryptDto();
        encryptDto.setKey(encryptKey);
        encryptDto.setOriginaldata("projectCode="+projectCode+"&timespan="+ DateConversion.formatDateTime(DateConversion.getFormatDate(new Date(),DateConversion.PRESCRITION_DATE_TIME)));
        String originaldata1=encrypt(encryptDto,drugsEnterprise);
        encryptDto.setOriginaldata(JSONUtils.toString(yfzAddHospitalPrescriptionDto));
        String originaldata2=encrypt(encryptDto,drugsEnterprise);

        String requestStr = JSONUtils.toString(originaldata2);
        Map<String,String> extendHeaders=new HashMap<String,String>();
        extendHeaders.put("Content-Type","application/json");
        extendHeaders.put("projectCode",projectCode);
        extendHeaders.put("encryptData",originaldata1);
        System.out.println("extendHeaders=============="+extendHeaders);
        System.out.println("originaldata1=============="+originaldata1);
        LOGGER.info("ByRemoteService.checkPrescriptionDrugStockHttpRequest:[{}][{}]查询库存请求，请求内容：{}", "id", "name", requestStr);
        System.out.println("url===="+drugsEnterprise.getBusinessUrl()+checkPrescriptionDrugStockHttpUrl);
        String outputData = HttpsClientUtils.doPost(drugsEnterprise.getBusinessUrl()+checkPrescriptionDrugStockHttpUrl, requestStr,extendHeaders);
        //获取响应消息
        LOGGER.info("ByRemoteService.checkPrescriptionDrugStockHttpRequest:[{}][{}]查询库存请求，获取响应消息：{}", "id", "name", JSONUtils.toString(outputData));
        YfzDecryptDto decryptDto=new YfzDecryptDto();
        decryptDto.setKey(encryptKey);
        decryptDto.setEncryptdata(outputData);
        Map resultMap = JSONUtils.parse(decrypt(decryptDto,drugsEnterprise), Map.class);
        String resCode = MapValueUtil.getString(resultMap, "code");
        String message = MapValueUtil.getString(resultMap, "message");
        if (RESULT_SUCCESS.equals(resCode)) {
            List<Map<String,Object>> yfzStoreBeans = MapValueUtil.getList(resultMap, "responseData");
            result.setCode(Integer.valueOf(resCode));
            List<DepDetailBean> detailList = new ArrayList<>();
            DepDetailBean detailBean;
            for (Map<String,Object> yfzStoreBean : yfzStoreBeans) {
                detailBean = new DepDetailBean();
                detailBean.setDepName(MapValueUtil.getString(yfzStoreBean, "name"));
                detailBean.setRecipeFee(BigDecimal.ZERO);
                detailBean.setExpressFee(BigDecimal.ZERO);
                detailBean.setPharmacyCode(MapValueUtil.getString(yfzStoreBean, "number"));
                detailBean.setDistance(0.00);
                detailBean.setAddress(MapValueUtil.getString(yfzStoreBean, "address"));
                Position position = new Position();
                position.setLatitude(0.00);
                position.setLongitude(0.00);
                detailBean.setPosition(position);
                detailList.add(detailBean);
            }
            result.setObject(detailList);
        }else{
           String responseData= MapValueUtil.getString(resultMap, "responseData");
            result.setCode(Integer.valueOf(resCode));
            result.setMsg(message + responseData);
//            getFailResult(result, message + responseData);
        }
        return result;
    }
    /**
     * @method  addHospitalPrescription
     * @description 发送http请求开处方
     * @date: 2020/02/20
     * @author: JRK
     * @param yfzAddHospitalPrescriptionDto
     * @param request 易复诊请求对象
     * @param httpclient http请求服务
     * @return void
     */
    @RpcService
    public DrugEnterpriseResult addHospitalPrescription(YfzAddHospitalPrescriptionDto yfzAddHospitalPrescriptionDto)throws IOException {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();

        LOGGER.info("ByRemoteService.corresPondingHospDrug:[{}][{}]获得新的处方药品信息", "id", "name");
        //发送请求，获得推送的结果
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
//            DrugEnterpriseResult result1=checkPrescriptionDrugStockHttpRequest(result,yfzAddHospitalPrescriptionDto);
//            if (result1.getCode().toString().equals(RESULT_SUCCESS)) {
//                addHospitalPrescriptionHttpRequest(result,yfzAddHospitalPrescriptionDto);
//            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("ByRemoteService.corresPondingHospDrug:[{}][{}]同步药品异常：{}","id", "name", e.getMessage());
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("ByRemoteService.corresPondingHospDrug:http请求资源关闭异常: {}！", e.getMessage());
            }
        }
        return result;
    }
    /**
     * @method  addHospitalPrescriptionHttpRequest
     * @description 开处方http请求
     * @date: 2019/7/10
     * @author: JRK
     * @param yfzAddHospitalPrescriptionDto 药企
     * @param getHospDrugDto 查询处方库存
     * @param httpClient 请求服务
     * @return void
     */
    private DrugEnterpriseResult addHospitalPrescriptionHttpRequest(DrugEnterpriseResult result, DrugsEnterprise drugsEnterprise,YfzAddHospitalPrescriptionDto yfzAddHospitalPrescriptionDto) throws IOException {
        YfzEncryptDto encryptDto=new YfzEncryptDto();
        encryptDto.setKey(drugsEnterprise.getToken());
        encryptDto.setOriginaldata("projectCode="+projectCode+"&timespan="+ DateConversion.formatDateTime(DateConversion.getFormatDate(new Date(),DateConversion.PRESCRITION_DATE_TIME)));
        String originaldata1=encrypt(encryptDto,drugsEnterprise);
        encryptDto.setOriginaldata(JSONUtils.toString(yfzAddHospitalPrescriptionDto));
        String originaldata2=encrypt(encryptDto,drugsEnterprise);

        String requestStr = JSONUtils.toString(originaldata2);
        Map<String,String> extendHeaders=new HashMap<String,String>();
        extendHeaders.put("Content-Type","application/json");
        extendHeaders.put("projectCode",projectCode);
        extendHeaders.put("encryptData",originaldata1);
        LOGGER.info("ByRemoteService.checkPrescriptionDrugStockHttpRequest:[{}][{}]查询库存请求，请求内容：{}", "id", "name", requestStr);
        String outputData = HttpsClientUtils.doPost(httpUrl+addHospitalPrescriptionHttpUrl, requestStr,extendHeaders);
        //获取响应消息
        LOGGER.info("ByRemoteService.checkPrescriptionDrugStockHttpRequest:[{}][{}]查询库存请求，获取响应消息：{}", "id", "name", JSONUtils.toString(outputData));
        YfzDecryptDto decryptDto=new YfzDecryptDto();
        decryptDto.setKey(drugsEnterprise.getToken());
        decryptDto.setEncryptdata(outputData);
        Map resultMap = JSONUtils.parse(decrypt(decryptDto,drugsEnterprise), Map.class);
        int resCode = MapValueUtil.getInteger(resultMap, "code");
        String message = MapValueUtil.getString(resultMap, "message");
        String responseData = MapValueUtil.getString(resultMap, "responseData");
        if (RESULT_SUCCESS.equals(resCode)) {
            result.setCode(resCode);
            result.setMsg(message + responseData);
        }else{
            result.setCode(resCode);
            getFailResult(result, message + responseData);
        }
        return result;
    }
    /**
     * 易复诊数据加密
     * @return
     */
    public String encrypt(Object encryptDto,DrugsEnterprise drugsEnterprise){
        LOGGER.info("ByRemoteService-encrypt encryptDto:{}.", encryptDto);
        String encryptData="";
        try{
            //开始发送请求数据
            String paramesRequest = JSONUtils.toString(encryptDto);
            LOGGER.info("ByRemoteService.encrypt paramesRequest:{}.", paramesRequest);
            //开始发送请求数据
            encryptData = HttpsClientUtils.doPost(drugsEnterprise.getBusinessUrl()+"yfz-cipher/AES/encrypt", paramesRequest);
            LOGGER.info("ByRemoteService.encrypt encryptData:{}.", encryptData);
        }catch(Exception e){
            LOGGER.error("ByRemoteService-encrypt error:{}.", e.getMessage(), e);
        }
        return encryptData;
    }
    /**
     * 易复诊数据解密
     * @return
     */
    public String decrypt(Object encryptDto,DrugsEnterprise drugsEnterprise){
        LOGGER.info("ByRemoteService-decrypt decryptDto:{}.", encryptDto);
        String decryptData="";
        try{
            //开始发送请求数据
            String paramesRequest = JSONUtils.toString(encryptDto);
            LOGGER.info("ByRemoteService.encrypt paramesRequest:{}.", paramesRequest);
            //开始发送请求数据
            decryptData = HttpsClientUtils.doPost(drugsEnterprise.getBusinessUrl()+"yfz-cipher/AES/decrypt", paramesRequest);
            LOGGER.info("ByRemoteService.decrypt decryptData:{}.", decryptData);
        }catch(Exception e){
            LOGGER.error("ByRemoteService-decrypt error:{}.", e.getMessage(), e);
        }
        return decryptData;
    }
    /**
     * @method  getFailResult
     * @description 失败操作的结果对象
     * @date: 2020/2/20
     * @author: JRK
     * @param result 返回的结果集对象
     * @param msg 失败提示的信息
     * @return
     */
    private void getFailResult(DrugEnterpriseResult result, String msg) {
        result.setMsg(msg);
        result.setCode(DrugEnterpriseResult.FAIL);
    }

    @Override
    public void tokenUpdateImpl(DrugsEnterprise drugsEnterprise) {

    }

    @RpcService
    public void test(Integer recipeId){
        List<Integer> recipeIds = Arrays.asList(recipeId);
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise enterprise = drugsEnterpriseDAO.getById(226);
//        pushRecipeInfo(recipeIds, enterprise);
//        scanStock(recipeId, enterprise);
        findSupportDep(recipeIds,null,enterprise);
    }
    @Override
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getFail();
        PatientService patientService = BasicAPI.getService(PatientService.class);
        DoctorService doctorService = BasicAPI.getService(DoctorService.class);
        DepartmentService departmentService = BasicAPI.getService(DepartmentService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);

        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);

        //根据处方ID获取处方信息
        List<Recipe> recipeList = recipeDAO.findByRecipeIds(recipeIds);
        YfzAddHospitalPrescriptionDto yfzAddHospitalPrescriptionDto=new YfzAddHospitalPrescriptionDto();
        if (CollectionUtils.isNotEmpty(recipeList)) {
            Recipe nowRecipe = recipeList.get(0);
            List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(nowRecipe.getRecipeId());
            //订单信息
            RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(nowRecipe.getOrderCode());
            //科室信息
            DepartmentDTO department=departmentService.getById(nowRecipe.getDepart());
            //医生信息
            DoctorDTO doctor = doctorService.get(nowRecipe.getDoctor());
            //患者信息
            PatientDTO patientDTO = patientService.get(nowRecipe.getMpiid());
            if(null == doctor){
                LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定医生不存在.", nowRecipe.getRecipeId());
                getFailResult(result, "处方绑定医生不存在");
                return result;
            }
            yfzAddHospitalPrescriptionDto.setAccess_token(enterprise.getToken());
            yfzAddHospitalPrescriptionDto.setHisprescriptionId(nowRecipe.getRecipeId().toString());
            yfzAddHospitalPrescriptionDto.setEmployeeCardNo(doctor.getLoginId().toString());
            yfzAddHospitalPrescriptionDto.setDoctorName(doctor.getName());
            yfzAddHospitalPrescriptionDto.setPrescriptionType("39");
            yfzAddHospitalPrescriptionDto.setDiagnoseName(nowRecipe.getMemo());
            yfzAddHospitalPrescriptionDto.setSymptoms(nowRecipe.getOrganDiseaseName());
            yfzAddHospitalPrescriptionDto.setDepartmentId(department.getDeptId().toString());
            yfzAddHospitalPrescriptionDto.setDepartmentName(department.getName());
            //设置患者信息
            YfzMesPatientDto mesPatient=new YfzMesPatientDto();
            mesPatient.setPatientSex(patientDTO.getPatientSex());
            mesPatient.setBirthday(DateConversion.getDateFormatter(patientDTO.getBirthday(),DateConversion.YYYY__MM__DD));
            mesPatient.setPhoneNo(patientDTO.getMobile());
            mesPatient.setPatientName(patientDTO.getPatientName());
            mesPatient.setCertificateId(patientDTO.getIdcard());
            mesPatient.setAreaCode("+86");
            yfzAddHospitalPrescriptionDto.setMesPatient(mesPatient);
            //设置处方单详细信息
            List<YfzMesDrugDetailDto> mesDrugDetailList=new ArrayList<YfzMesDrugDetailDto>();
            for (Recipedetail recipedetail : recipedetails) {
                YfzMesDrugDetailDto dto=new YfzMesDrugDetailDto();
                Integer drugId = recipedetail.getDrugId();
                String drugSpec = recipedetail.getDrugSpec();
                Double useTotalDose = recipedetail.getUseTotalDose();
                BigDecimal totalPrice=recipedetail.getTotalPrice();
                String memo=recipedetail.getMemo();
                BigDecimal totalDose = new BigDecimal(useTotalDose);
                String usingRate ;
                String usePathways ;
                try {
                    usingRate = DictionaryController.instance().get("eh.cdr.dictionary.UsingRate").getText(recipedetail.getUsingRate());
                    usePathways = DictionaryController.instance().get("eh.cdr.dictionary.UsePathways").getText(recipedetail.getUsePathways());
                } catch (ControllerException e) {
                    return getDrugEnterpriseResult(result, "药物使用频率使用途径获取失败");
                }
                dto.setDrugId(drugId.toString());
                dto.setSpec(drugSpec);
                dto.setForm(usePathways+usingRate);
                dto.setAmount(String.valueOf(useTotalDose));
                dto.setHospDrugPrice(String.valueOf(totalPrice));
                dto.setDrugMark(memo);
                mesDrugDetailList.add(dto);
            }
            yfzAddHospitalPrescriptionDto.setMesDrugDetailList(mesDrugDetailList);
            //设置订单信息
            YfzTBPrescriptionExtendDto tbpDto=new YfzTBPrescriptionExtendDto();
            if(nowRecipe.getGiveMode().equals("1")){
                tbpDto.setCostCategory("1");
            }else{
                tbpDto.setCostCategory(nowRecipe.getGiveMode().toString());
            }
            tbpDto.setPrescriptionNo(nowRecipe.getRecipeId().toString());
            tbpDto.setReceiverName(recipeOrder.getReceiver());
            tbpDto.setReceiverMobile(recipeOrder.getRecMobile());
            tbpDto.setReceiverAddress(getCompleteAddress(recipeOrder));
            tbpDto.setPayDatetime(DateConversion.getFormatDate(recipeOrder.getPayTime(),DateConversion.YYYY_MM_DD));
            tbpDto.setOrderType("1");
            tbpDto.setOrderCreateDate(DateConversion.getDateFormatter(recipeOrder.getCreateTime(),DateConversion.DEFAULT_DATE_TIME));
            tbpDto.setOrderPayDate(DateConversion.getDateFormatter(recipeOrder.getPayTime(),DateConversion.DEFAULT_DATE_TIME));
            tbpDto.setDeliverypPrice(String.valueOf(recipeOrder.getExpressFee()));
            tbpDto.setOrderDrugPrice(String.valueOf(recipeOrder.getTotalFee()));
            tbpDto.setOrderStatus("9");
            tbpDto.setOrderSource("1");
            tbpDto.setMemo(nowRecipe.getMemo());
            yfzAddHospitalPrescriptionDto.setTbPrescriptionExtend(tbpDto);
            //发送请求，获得推送的结果
            CloseableHttpClient httpClient = HttpClients.createDefault();
            try {
                DrugEnterpriseResult result2=scanStock(nowRecipe.getRecipeId(),enterprise);
            if (result2.getCode().equals(200)) {
                YfzTADrugStoreDto yfzTADrugStoreDto=new YfzTADrugStoreDto();
                yfzTADrugStoreDto.setId(recipeOrder.getDrugStoreCode());
                yfzTADrugStoreDto.setAddress(recipeOrder.getDrugStoreAddr());
                yfzTADrugStoreDto.setName(recipeOrder.getDrugStoreName());
                addHospitalPrescriptionHttpRequest(result,enterprise, yfzAddHospitalPrescriptionDto);
            }
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("ByRemoteService.corresPondingHospDrug:[{}][{}]推送处方异常：{}",nowRecipe.getDepart(), "name", e.getMessage());
            } finally {
                try {
                    httpClient.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.error("ByRemoteService.corresPondingHospDrug:http请求资源关闭异常: {}！", e.getMessage());
                }
            }

        }else{
            LOGGER.warn("ByRemoteService.pushRecipeInfo:未查询到匹配的处方列表");
            getFailResult(result, "未查询到匹配的处方列表");
            return result;
        }
        return result;
    }

    @Override
    public DrugEnterpriseResult pushRecipe(HospitalRecipeDTO hospitalRecipeDTO, DrugsEnterprise enterprise) {
        return null;
    }

    @Override
    public DrugEnterpriseResult scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getFail();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipeId);
        //药品详情封装
        YfzCheckPrescriptionDrugStockDto yfzAddHospitalPrescriptionDto=new YfzCheckPrescriptionDrugStockDto();
        yfzAddHospitalPrescriptionDto.setAccess_token(drugsEnterprise.getToken());
        List<YfzMesDrugDetailDto> mesDrugDetailList=new ArrayList<YfzMesDrugDetailDto>();
        for(Recipedetail recipedetail:recipedetails){
            YfzMesDrugDetailDto yfzMesDrugDetailDto=new YfzMesDrugDetailDto();
            yfzMesDrugDetailDto.setDrugId(recipedetail.getDrugId().toString());
            yfzMesDrugDetailDto.setAmount(String.valueOf(recipedetail.getUseTotalDose()));
            mesDrugDetailList.add(yfzMesDrugDetailDto);
        }
        yfzAddHospitalPrescriptionDto.setMesDrugDetailList(mesDrugDetailList);

        //发送请求，获得推送的结果
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            if (drugsEnterprise.getBusinessUrl().contains("http:")) {
            result=checkPrescriptionDrugStockHttpRequest(result,drugsEnterprise,yfzAddHospitalPrescriptionDto);
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("ByRemoteService.checkPrescriptionDrugStockHttpRequest:[{}][{}]同步药品异常：{}",recipe.getDepart(), recipe.getDoctor(), e.getMessage());
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("ByRemoteService.checkPrescriptionDrugStockHttpRequest:http请求资源关闭异常: {}！", e.getMessage());
            }
        }
        return result;
    }

    @Override
    public DrugEnterpriseResult syncEnterpriseDrug(DrugsEnterprise drugsEnterprise, List<Integer> drugIdList) {
        return null;
    }

    @Override
    public DrugEnterpriseResult pushCheckResult(Integer recipeId, Integer checkFlag, DrugsEnterprise enterprise) {
        return null;
    }

    @Override
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getFail();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Recipe> recipeList = recipeDAO.findByRecipeIds(recipeIds);
        if (CollectionUtils.isNotEmpty(recipeList)) {
            Recipe nowRecipe = recipeList.get(0);
            result=scanStock(nowRecipe.getRecipeId(),enterprise);
        }else{
            LOGGER.warn("ByRemoteService.findSupportDep:未查询到匹配的处方列表");
            getFailResult(result, "未查询到匹配的处方列表");
            return result;
        }

        return result;
    }

    @Override
    public String getDrugEnterpriseCallSys() {
        return DrugEnterpriseConstant.COMPANY_BY;
    }

    public String getCompleteAddress(RecipeOrder order) {
        StringBuilder address = new StringBuilder();
        if (null != order) {
            this.getAddressDic(address, order.getAddress1());
            this.getAddressDic(address, order.getAddress2());
            this.getAddressDic(address, order.getAddress3());
            address.append(StringUtils.isEmpty(order.getAddress4()) ? "" : order.getAddress4());
        }
        return address.toString();
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
}