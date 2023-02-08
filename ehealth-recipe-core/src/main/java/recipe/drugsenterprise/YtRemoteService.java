package recipe.drugsenterprise;

import com.alibaba.fastjson.JSON;
import com.ngari.patient.dto.DepartmentDTO;
import com.ngari.patient.dto.DoctorDTO;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.*;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.drugsenterprise.model.DepDetailBean;
import com.ngari.recipe.drugsenterprise.model.DrugsDataBean;
import com.ngari.recipe.drugsenterprise.model.Position;
import com.ngari.recipe.dto.DrugInfoDTO;
import com.ngari.recipe.dto.DrugStockAmountDTO;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.hisprescription.model.HospitalRecipeDTO;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;
import recipe.ApplicationUtils;
import recipe.bean.DrugEnterpriseResult;
import recipe.common.CommonConstant;
import recipe.constant.DrugEnterpriseConstant;
import recipe.constant.RecipeBussConstant;
import recipe.dao.*;
import recipe.drugsenterprise.bean.*;
import recipe.service.RecipeLogService;
import recipe.third.IFileDownloadService;
import recipe.util.DateConversion;
import recipe.util.DictionaryUtil;
import recipe.util.DistanceUtil;
import recipe.util.HttpHelper;

import javax.annotation.Resource;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
* @Description: YtRemoteService 类（或接口）是 对接英特药企服务接口
* @Author: JRK
* @Date: 2019/7/8
*/
@RpcBean("ytRemoteService")
public class YtRemoteService extends AccessDrugEnterpriseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtRemoteService.class);

    private static final String pushRecipeHttpUrl = "prescriptions/digit";

    private static final String getStock = "medicine";

    private static final String searchMapRANGE = "range";

    private static final String searchMapLatitude = "latitude";

    private static final String searchMapLongitude = "longitude";

    private static final String ytPayMethod = "0";

    private static final String ytSendMethod = "1";

    private static final String requestHeadJsonKey = "Content-Type";

    private static final String requestHeadJsonValue = "application/json";

    private static final String requestHeadPowerKey = "Authorization";

    private static final Integer requestPushSuccessCode = 204;

    private static final String imgHead = "data:image/jpeg;base64,";

    private static final String YT_SY = "yt_sy";

    @Resource
    private SaleDrugListDAO saleDrugListDAO;
    @Resource
    private RecipeParameterDao recipeParameterDao;
    @Resource
    private RecipeDAO recipeDAO;
    @Resource
    private RecipeOrderDAO recipeOrderDAO;

    @RpcService
    public void test(Integer depId){
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        tokenUpdateImpl(drugsEnterpriseDAO.getById(depId));
    }

    @Override
    @RpcService
    public void tokenUpdateImpl(DrugsEnterprise drugsEnterprise) {
        LOGGER.info("YtRemoteService tokenUpdateImpl drugsEnterprise:{}", JSON.toJSONString(drugsEnterprise));
        if (StringUtils.isEmpty(drugsEnterprise.getAuthenUrl())) {
            return;
        }
        DrugsEnterpriseDAO drugsEnterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        YtTokenRequest request = new YtTokenRequest();
        request.setUser(drugsEnterprise.getUserId());
        request.setPassword(drugsEnterprise.getPassword());
        String organization = recipeParameterDao.getByName("organization");
        request.setOrganization(organization);
        LOGGER.info("YtRemoteService tokenUpdateImpl request:{}", JSON.toJSONString(request));
        try {
            String response = HttpHelper.doPost(drugsEnterprise.getAuthenUrl(), JSON.toJSONString(request) ,"");
            LOGGER.info("YtRemoteService tokenUpdateImpl response:{}", response);
            YtTokenResponse tokenResponse = JSONUtils.parse(response, YtTokenResponse.class);
            String newToken = tokenResponse.getTOKEN();
            if(StringUtils.isNotEmpty(newToken)) {
                drugsEnterpriseDAO.updateTokenById(drugsEnterprise.getId(), newToken);
                LOGGER.info("YtRemoteService tokenUpdateImpl:[{}][{}]token更新成功:{}", drugsEnterprise.getId(), drugsEnterprise.getName(), newToken);
            }
        } catch (IOException e) {
            LOGGER.error("YtRemoteService tokenUpdateImpl error", e);
        }
    }

    @Override
    public DrugStockAmountDTO scanEnterpriseDrugStock(Recipe recipe, DrugsEnterprise drugsEnterprise, List<Recipedetail> recipeDetails) {
        LOGGER.info("scanEnterpriseDrugStock recipeDetails:{}.", JSONUtils.toString(recipeDetails));
        DrugStockAmountDTO drugStockAmountDTO = new DrugStockAmountDTO();
        List<DrugInfoDTO> drugInfoList = new ArrayList<>();
        List<Integer> drugList = recipeDetails.stream().map(Recipedetail::getDrugId).collect(Collectors.toList());
        List<SaleDrugList> saleDrugLists = saleDrugListDAO.findByOrganIdAndDrugIdsEffectivity(drugsEnterprise.getId(), drugList);
        Map<Integer, SaleDrugList> saleDrugListMap = saleDrugLists.stream().collect(Collectors.toMap(SaleDrugList::getDrugId,a->a,(k1,k2)->k1));
        recipeDetails.forEach(recipeDetail -> {
            DrugInfoDTO drugInfoDTO = new DrugInfoDTO();
            SaleDrugList saleDrugList = saleDrugListMap.get(recipeDetail.getDrugId());
            drugInfoDTO.setStock(false);
            drugInfoDTO.setDrugId(recipeDetail.getDrugId());
            drugInfoDTO.setDrugName(recipeDetail.getDrugName());
            drugInfoDTO.setOrganDrugCode(recipeDetail.getOrganDrugCode());
            drugInfoDTO.setUseTotalDose(recipeDetail.getUseTotalDose());
            if (null != saleDrugList) {
                String inventory = getDrugInventory(recipeDetail.getDrugId(), drugsEnterprise, recipe.getClinicOrgan());
                if (!"0".equals(inventory)) {
                    drugInfoDTO.setStock(true);
                    drugInfoDTO.setStockAmountChin(inventory);
                } else {
                    drugInfoDTO.setStockAmountChin("0");
                }
            }
            drugInfoList.add(drugInfoDTO);
        });
        super.setDrugStockAmountDTO(drugStockAmountDTO, drugInfoList);
        return drugStockAmountDTO;
    }

    @Override
    public String getDrugInventory(Integer drugId, DrugsEnterprise drugsEnterprise, Integer organId) {
        SaleDrugList saleDrug = saleDrugListDAO.getByDrugIdAndOrganId(drugId, drugsEnterprise.getId());
        Pharmacy pharmacy = new Pharmacy();
        if (YT_SY.equals(drugsEnterprise.getAccount())) {
            pharmacy.setPharmacyCode("YK45286");
        } else {
            //设置指定药店配送
            String store = recipeParameterDao.getByName(organId + "_yt_store_code");
            pharmacy.setPharmacyCode(store);
        }
        String stockResponse = getInventoryResult(drugsEnterprise, saleDrug, pharmacy);
        if (stockResponse != null) return stockResponse;
        return "0";
    }

    private String getInventoryResult(DrugsEnterprise drugsEnterprise, SaleDrugList saleDrug, Pharmacy pharmacy) {
        if (YT_SY.equals(drugsEnterprise.getAccount())) {
            try {
                String stockUrl = drugsEnterprise.getBusinessUrl() + getStock + "/" + pharmacy.getPharmacyCode() + "/" + saleDrug.getOrganDrugCode();
                String responseStr = HttpHelper.doGet(stockUrl, drugsEnterprise.getToken());
                LOGGER.info("YtRemoteService getDrugInventory:[{}]门店该[{}]药品查询库存，请求返回:{}", pharmacy.getPharmacyCode(), saleDrug.getOrganDrugCode(), responseStr);
                YtStockResponse stockResponse = JSONUtils.parse(responseStr, YtStockResponse.class);
                if (stockResponse.getStock() == 0.0) {
                    return "0";
                }
                return stockResponse.getStock().toString();
            }catch (Exception e){
                LOGGER.info("YtRemoteService.getDrugInventory:运营平台查询药品库存失败, {},{},{}", saleDrug.getDrugId(), drugsEnterprise.getName(), e.getMessage(),e);
            }
        } else {
            Map<String, String> requestMap = new HashMap<>();
            String orgCode = DictionaryUtil.getDictionary("eh.recipe.orgCode", drugsEnterprise.getId());
            String hospitalCode = DictionaryUtil.getDictionary("eh.recipe.hospitalCode", drugsEnterprise.getId());
            requestMap.put("orgCode ", orgCode);
            requestMap.put("code ", saleDrug.getOrganDrugCode());
            requestMap.put("hospitalCode ", hospitalCode);
            String request = JSON.toJSONString(requestMap);
            LOGGER.info("YtRemoteService getDrugInventory request:{}", request);
            try {
                String responseStr = HttpHelper.doPost(drugsEnterprise.getBusinessUrl(), request, drugsEnterprise.getToken());
                LOGGER.info("YtRemoteService getDrugInventory responseStr:{}", responseStr);
                YtStockResponse stockResponse = JSONUtils.parse(responseStr, YtStockResponse.class);
                if (stockResponse.getStock() == 0.0) {
                    return "0";
                }
                return stockResponse.getStock().toString();
            } catch (IOException e) {
                LOGGER.error("YtRemoteService getDrugInventory error", e);
            }
        }
        return "0";
    }

    @Override
    public List<String> getDrugInventoryForApp(DrugsDataBean drugsDataBean, DrugsEnterprise drugsEnterprise, Integer flag) {
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        List<String> result = new ArrayList<>();
        if (new Integer(1).equals(flag)) {
            //配送
            for (RecipeDetailBean recipeDetailBean : drugsDataBean.getRecipeDetailBeans()) {
                SaleDrugList saleDrugList = saleDrugListDAO.getByDrugIdAndOrganIdAndStatus(recipeDetailBean.getDrugId(), drugsEnterprise.getId());
                if (Objects.isNull(saleDrugList)) {
                    return result;
                }
                String drugInventory = getDrugInventory(saleDrugList.getDrugId(), drugsEnterprise, drugsDataBean.getOrganId());
                if (!"暂不支持库存查询".equals(drugInventory)) {
                    try {
                        Double inventory = Double.parseDouble(drugInventory);
                        if (recipeDetailBean.getUseTotalDose() <= inventory) {
                            result.add(recipeDetailBean.getDrugName());
                        }
                    } catch(Exception e){
                        LOGGER.info("YtRemoteService.getDrugInventoryForApp:医生端查询药品库存失败,{},{}", drugsEnterprise.getName(), e.getMessage(),e);
                    }
                }
            }
        } else {
            //药店取药
            PharmacyDAO pharmacyDAO = DAOFactory.getDAO(PharmacyDAO.class);
            List<Pharmacy> pharmacies = pharmacyDAO.findByDrugsenterpriseIdAndStatus(drugsEnterprise.getId(), 1);
            for (RecipeDetailBean recipeDetailBean : drugsDataBean.getRecipeDetailBeans()) {
                SaleDrugList saleDrugList = saleDrugListDAO.getByDrugIdAndOrganId(recipeDetailBean.getDrugId(), drugsDataBean.getOrganId());
                if (null == saleDrugList) {
                    return result;
                }
                for (Pharmacy pharmacy : pharmacies) {
                    String stockResponse = getInventoryResult(drugsEnterprise, saleDrugList, pharmacy);
                    if (StringUtils.isEmpty(stockResponse)) {
                        continue;
                    }
                    if (!"暂不支持库存查询".equals(stockResponse)) {
                        try {
                            Double inventory = Double.parseDouble(stockResponse);
                            if (recipeDetailBean.getUseTotalDose() <= inventory) {
                                result.add(recipeDetailBean.getDrugName());
                                break;
                            }
                        } catch(Exception e){
                            LOGGER.info("YtRemoteService.getDrugInventoryForApp:医生端药店取药查询药品库存失败,{},{}", drugsEnterprise.getName(), e.getMessage(),e);
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    @RpcService
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        LOGGER.info("pushRecipeInfo:{}.", JSONUtils.toString(recipeIds));
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        Integer depId = enterprise.getId();
        String depName = enterprise.getName();
        //首先校验请求的数据是否合规
        if (StringUtils.isEmpty(enterprise.getBusinessUrl())) {
            LOGGER.warn("YtRemoteService.pushRecipeInfo:[{}][{}]药企的访问url为空", depId, depName);
            getFailResult(result, "药企的访问url为空");
            return result;
        }
        if (CollectionUtils.isEmpty(recipeIds)) {
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方列表为空");
            getFailResult(result, "处方列表为空");
            return result;
        }
        //组装YtRecipeDTO和处方明细下的YtDrugDTO
        YtRecipeDTO sendYtRecipe = new YtRecipeDTO();
        List<Recipe> recipeList = recipeDAO.findByRecipeIds(recipeIds);
        if (CollectionUtils.isNotEmpty(recipeList)) {
            Recipe nowRecipe = recipeList.get(0);
            getMedicalInfo(nowRecipe);
            assemblePushRecipeMessage(result, sendYtRecipe, nowRecipe, enterprise);
            if (DrugEnterpriseResult.FAIL.equals(result.getCode())) {
                return result;
            }
            //发送请求，获得推送的结果
            pushRecipeHttpRequest(result, enterprise, sendYtRecipe, nowRecipe);
        }else{
            LOGGER.warn("YtRemoteService.pushRecipeInfo:未查询到匹配的处方列表");
            getFailResult(result, "未查询到匹配的处方列表");
            return result;
        }
        return result;
    }

    @Override
    public DrugEnterpriseResult pushRecipe(HospitalRecipeDTO hospitalRecipeDTO, DrugsEnterprise enterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    /**
     * @method  pushRecipeHttpRequest
     * @description 推送处方http请求
     * @date: 2019/7/10
     * @author: JRK
     * @param enterprise 药企
     * @param sendYtRecipe 推送的处方信息
     * @return void
     */
    private void pushRecipeHttpRequest(DrugEnterpriseResult result, DrugsEnterprise enterprise, YtRecipeDTO sendYtRecipe, Recipe recipe) {
        String url;
        if (YT_SY.equals(enterprise.getAccount())) {
            url = enterprise.getBusinessUrl() + pushRecipeHttpUrl;
        } else {
            url = enterprise.getBusinessUrl();
        }
        try {
            String requestStr = JSONUtils.toString(sendYtRecipe);
            int statusCode = HttpHelper.getStatusCode(url, requestStr, enterprise.getToken());
            if(requestPushSuccessCode == statusCode) {
                if (recipe != null) {
                    LOGGER.info("YtRemoteService pushRecipeInfo:[{}][{}][{}]处方推送成功.", recipe.getRecipeId() ,enterprise.getId(), enterprise.getName());
                    RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "纳里给"+enterprise.getName()+"推送处方成功");
                    recipeOrderDAO.updatePushFlagByOrderCode(recipe.getOrderCode(), 1);
                } else {
                    LOGGER.info("YtRemoteService pushRecipeInfo:[{}][{}]处方推送成功.",enterprise.getId(), enterprise.getName());
                }
            }else{
                if (recipe != null) {
                    LOGGER.info("YtRemoteService pushRecipeInfo:[{}][{}][{}]处方推送失败.", recipe.getRecipeId() ,enterprise.getId(), enterprise.getName());
                    RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "纳里给"+enterprise.getName()+"推送处方失败");
                    recipeOrderDAO.updatePushFlagByOrderCode(recipe.getOrderCode(), -1);
                } else {
                    LOGGER.info("YtRemoteService pushRecipeInfo:[{}][{}]处方推送失败.",enterprise.getId(), enterprise.getName());
                }
                getFailResult(result, "处方推送失败");
            }
        } catch (Exception e) {
            LOGGER.error("YtRemoteService pushRecipeInfo error", e);
        }
    }

    /**
     * @method  assemblePushRecipeMessage
     * @description 组装推送的处方数据
     * @date: 2019/7/10
     * @author: JRK
     * @param result 返回的操作结果
     * @param organService 机构service
     * @param sendYtRecipe 组装的订单信息
     * @param nowRecipe 当前的订单
     * @return recipe.bean.DrugEnterpriseResult 操作的结果集
     */
    private void assemblePushRecipeMessage(DrugEnterpriseResult result, YtRecipeDTO sendYtRecipe, Recipe nowRecipe, DrugsEnterprise enterprise) {
        OrganService organService = BasicAPI.getService(OrganService.class);
        //检验并组装处方对应的机构信息
        if(null == nowRecipe){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},处方不存在,", nowRecipe.getRecipeId());
            getFailResult(result, "处方单不存在");
            return;
        }
        if (null == nowRecipe.getClinicOrgan()) {
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定的机构id为空.", nowRecipe.getRecipeId());
            getFailResult(result, "开处方机构编码不存在");
            return;
        }

        OrganDTO organ = organService.getByOrganId(nowRecipe.getClinicOrgan());
        if(YT_SY.equals(enterprise.getAccount())) {
            //这里保存的医院的社保编码
            sendYtRecipe.setHospitalCode(organ.getOrganizeCode());
        } else {
            String hospitalCode = DictionaryUtil.getDictionary("eh.recipe.hospitalCode", enterprise.getId());
            sendYtRecipe.setHospitalCode(hospitalCode);
        }
        sendYtRecipe.setHospitalName(nowRecipe.getOrganName());
        //检验并组装处方信息（上面已经校验过了）
        assembleRecipeMsg(sendYtRecipe, nowRecipe, enterprise);
        //检验并组装处方对应的医生信息
        assembleDoctorMsg(result, sendYtRecipe, nowRecipe);
        if(RecipeResultBean.FAIL.equals(result.getCode())){
            return;
        }
        //检验并组装处方对应的患者信息
        assemblePatientMsg(result, sendYtRecipe, nowRecipe);
        if(RecipeResultBean.FAIL.equals(result.getCode())){
            return;
        }
        //检验并组装处方对应的科室信息
        assembleDepartMsg(result, sendYtRecipe, nowRecipe);
        if(RecipeResultBean.FAIL.equals(result.getCode())){
            return;
        }
        //检验并组装处方对应的门店信息
        assembleStoreMsg(result, sendYtRecipe, nowRecipe, enterprise);
        if(RecipeResultBean.FAIL.equals(result.getCode())){
            return;
        }
        //设置处方笺base
        String ossId = nowRecipe.getSignImg();
        if(null != ossId){
            try {
                IFileDownloadService fileDownloadService = ApplicationUtils.getBaseService(IFileDownloadService.class);
                String imgStr = imgHead + fileDownloadService.downloadImg(ossId);
                if(ObjectUtils.isEmpty(imgStr)){
                    LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{}的ossid为{}处方笺不存在", nowRecipe.getRecipeId(), ossId);
                    getFailResult(result, "处方笺不存在");
                    return;
                }
                LOGGER.warn("YtRemoteService.pushRecipeInfo:{}处方，下载处方笺服务成功", nowRecipe.getRecipeId());
                sendYtRecipe.setImage(imgStr);
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.warn("YtRemoteService.pushRecipeInfo:{}处方，下载处方笺服务异常：{}.", nowRecipe.getRecipeId(), e.getMessage(),e );
                getFailResult(result, "下载处方笺服务异常");
                return;
            }

        }
        //检验并组装处方对应的详情信息
        assembleDrugListMsg(result, sendYtRecipe, nowRecipe, organ, enterprise);
    }
    /**
     * @method  assembleRecipeMsg
     * @description 组装推送的处方单中的处方信息
     * @date: 2019/7/10
     * @author: JRK
     * @param sendYtRecipe 推送的处方单信息
     * @param nowRecipe 当前的处方单
     * @return void
     */
    private void assembleRecipeMsg(YtRecipeDTO sendYtRecipe, Recipe nowRecipe, DrugsEnterprise enterprise) {
        sendYtRecipe.setSourceSerialNumber(nowRecipe.getRecipeCode());
        sendYtRecipe.setExternalNumber("");
        sendYtRecipe.setHzMedicalFlag(2);
        sendYtRecipe.setpTime(DateConversion.getDateFormatter(nowRecipe.getSignDate(), "yyyy-MM-dd"));
        sendYtRecipe.setValidDay(3);

        sendYtRecipe.setRecordNo(nowRecipe.getPatientID());

        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        if (StringUtils.isNotEmpty(nowRecipe.getOrderCode())) {
            RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(nowRecipe.getOrderCode());
            if (recipeOrder != null) {
                if (null != recipeOrder.getExpressFee()) {
                    sendYtRecipe.setTransFee(recipeOrder.getExpressFee().doubleValue());
                } else {
                    sendYtRecipe.setTransFee(0.0);
                }
                sendYtRecipe.setServiceFree(recipeOrder.getRegisterFee().doubleValue());
                sendYtRecipe.setPrescriptionChecking(recipeOrder.getAuditFee().doubleValue());
                sendYtRecipe.setTotalAmount(recipeOrder.getTotalFee().doubleValue());
                sendYtRecipe.setOrderNo(recipeOrder.getOutTradeNo());
                if (recipeOrder.getOrderType() != null && recipeOrder.getOrderType() == 1) {
                    sendYtRecipe.setCostType(2);
                } else {
                    sendYtRecipe.setCostType(1);
                }
                if (recipeOrder.getOrderType() == 1 && recipeOrder.getFundAmount() != null) {
                    sendYtRecipe.setFundAmount(recipeOrder.getFundAmount());
                } else {
                    sendYtRecipe.setFundAmount(0.0);
                }
                if (1 == nowRecipe.getGiveMode()) {
                    sendYtRecipe.setGiveModel(1);
                } else if (3 == nowRecipe.getGiveMode()) {
                    sendYtRecipe.setGiveModel(0);
                }
                String province = getAddressDic(recipeOrder.getAddress1());
                String city = getAddressDic(recipeOrder.getAddress2());
                String district = getAddressDic(recipeOrder.getAddress3());
                sendYtRecipe.setProvince(province);
                sendYtRecipe.setCity(city);
                sendYtRecipe.setDistrict(district);
                // 线上支付为已支付,线下支付为未支付
                if (RecipeBussConstant.PAYMODE_ONLINE.equals(recipeOrder.getPayMode())) {
                    sendYtRecipe.setIfPay(1);
                } else {
                    sendYtRecipe.setIfPay(0);
                }
            }
        }
        if (YT_SY.equals(enterprise.getAccount())) {
            sendYtRecipe.setSource(4);
        } else {
            sendYtRecipe.setSource("nali");
        }

        sendYtRecipe.setRecipeId(nowRecipe.getRecipeId());
        sendYtRecipe.setDiagnose(nowRecipe.getOrganDiseaseName());
        sendYtRecipe.setRecipeType(nowRecipe.getRecipeType());
    }

    /**
     * @method  assembleDrugListMsg
     * @description 组装推送的处方单中药品详情的信息
     * @date: 2019/7/10
     * @author: JRK
     * @param sendYtRecipe 推送的处方单信息
     * @param nowRecipe 当前处方
     * @param organ 所属机构
     * @return recipe.bean.DrugEnterpriseResult 操作结果集
     */
    private void assembleDrugListMsg(DrugEnterpriseResult result, YtRecipeDTO sendYtRecipe, Recipe nowRecipe, OrganDTO organ ,DrugsEnterprise enterprise) {
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Recipedetail> detailList = detailDAO.findByRecipeId(nowRecipe.getRecipeId());
        if(CollectionUtils.isEmpty(detailList)){
            LOGGER.warn("YtRemoteService pushRecipeInfo:处方ID为{},绑定订单不存在.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定订单不存在");
            return;
        }
        List<YtDrugDTO> drugList = new ArrayList<>();
        sendYtRecipe.setItemList(drugList);
        double price = 0d;
        double quantity = 0d;
        YtDrugDTO nowYtDrugDTO;
        SaleDrugList saleDrug;
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        for (Recipedetail nowDetail : detailList) {
            nowYtDrugDTO = new YtDrugDTO();
            drugList.add(nowYtDrugDTO);
            nowYtDrugDTO.setSourceSerialNumber(nowRecipe.getRecipeCode());
            nowYtDrugDTO.setHospitalCode(organ.getOrganizeCode());
            if(null == nowDetail.getRecipeDetailId()){
                LOGGER.warn("YtRemoteService pushRecipeInfo:当前处方细节id不存在");
                getFailResult(result, "当前处方细节id不存在");
                return;
            }
            nowYtDrugDTO.setItemNo(nowDetail.getRecipeDetailId().toString());
            //根据药品id和所属的药企在saleDrug下获取药品的编码
            saleDrug = saleDrugListDAO.getByDrugIdAndOrganId(nowDetail.getDrugId(), enterprise.getId());
            if(null == saleDrug) {
                LOGGER.warn("YtRemoteService.pushRecipeInfo:处方细节ID为{},对应销售药品的信息不存在", nowDetail.getRecipeDetailId());
                getFailResult(result, "销售药品的信息不存在");
                return;
            }
            nowYtDrugDTO.setCode(saleDrug.getOrganDrugCode());
            if(null == nowDetail.getSalePrice()) {
                LOGGER.warn("YtRemoteService.pushRecipeInfo:处方细节ID为{},药品的单价为空", nowDetail.getRecipeDetailId());
                getFailResult(result, "药品的单价为空");
                return;
            }
            price = nowDetail.getActualSalePrice() == null ? nowDetail.getSalePrice().doubleValue() : nowDetail.getActualSalePrice().doubleValue();
            quantity = nowDetail.getUseTotalDose();

            nowYtDrugDTO.setQuantity(quantity);
            nowYtDrugDTO.setPrice(price);
            nowYtDrugDTO.setAmount(price * quantity);
            nowYtDrugDTO.setUsage(nowDetail.getUsingRate());
            nowYtDrugDTO.setDosage(nowDetail.getUseDose() + nowDetail.getUseDoseUnit());
            String usepathWays = nowDetail.getUsePathways();
            try {
                String usePathwaysText = DictionaryController.instance().get("eh.cdr.dictionary.UsePathways").getText(nowDetail.getUsePathways());
                usepathWays+="("+usePathwaysText+")";
            } catch (ControllerException e) {
                LOGGER.error("YtRemoteService.pushRecipeInfo:处方细节ID为{},药品的单价为空", nowDetail.getRecipeDetailId(),e);
                getFailResult(result, "药品用法出错");
                return;
            }
            nowYtDrugDTO.setPeroral(usepathWays);
        }
    }

    /**
     * @method  assembleDoctorMsg
     * @description 组装推送的处方单中医生的信息
     * @date: 2019/7/10
     * @author: JRK
     * @param sendYtRecipe 推送的处方单信息
     * @param nowRecipe 当前处方
     * @return recipe.bean.DrugEnterpriseResult 操作结果集
     */
    private void assembleDoctorMsg(DrugEnterpriseResult result, YtRecipeDTO sendYtRecipe, Recipe nowRecipe) {
        DoctorService doctorService = BasicAPI.getService(DoctorService.class);
        if(null == nowRecipe.getDoctor()){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定医生id为空.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定医生id为空");
            return;
        }
        DoctorDTO doctor = doctorService.get(nowRecipe.getDoctor());
        if(null == doctor){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定医生不存在.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定医生不存在");
            return;
        }
        sendYtRecipe.setDoctorName(doctor.getName());
    }

    /**
     * @method  assembleStoreMsg
     * @description 组装推送的处方单中药店的信息
     * @date: 2019/7/10
     * @author: JRK
     * @param sendYtRecipe 推送的处方单信息
     * @param nowRecipe 当前处方
     * @return recipe.bean.DrugEnterpriseResult 操作结果集
     */
    private void assembleStoreMsg(DrugEnterpriseResult result, YtRecipeDTO sendYtRecipe, Recipe nowRecipe, DrugsEnterprise enterprise) {
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        if(null == nowRecipe.getOrderCode()){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定订单code为空.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定订单code为空");
            return;
        }
        RecipeOrder order = orderDAO.getByOrderCode(nowRecipe.getOrderCode());
        if(null == order){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定订单不存在.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定订单不存在");
            return;
        }
        if (nowRecipe.getGiveMode() == 1) {
            //表示配送到家
            RecipeParameterDao recipeParameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
            //根据机构_yt_store_code获取配送药店
            Integer organId = nowRecipe.getClinicOrgan();
            if (StringUtils.equalsIgnoreCase("yt_sy", enterprise.getAccount())) {
                String store_code = organId + "_" + "yt_sy_store_code";
                String storeCode = recipeParameterDao.getByName(store_code);
                LOGGER.info("assembleStoreMsg 商业推送:{}.", storeCode);
                sendYtRecipe.setOrgCode(storeCode);
            } else {
                String orgCode = DictionaryUtil.getDictionary("eh.recipe.orgCode", enterprise.getId());
                sendYtRecipe.setOrgCode(orgCode);
            }
        } else {
            sendYtRecipe.setOrgCode(order.getDrugStoreCode());
        }
    }

    /**
     * @method  assembleDepartMsg
     * @description 组装推送的处方单中科室的信息
     * @date: 2019/7/10
     * @author: JRK
     * @param sendYtRecipe 推送的处方单信息
     * @param nowRecipe 当前处方
     * @return recipe.bean.DrugEnterpriseResult 操作结果集
     */
    private void assembleDepartMsg(DrugEnterpriseResult result, YtRecipeDTO sendYtRecipe, Recipe nowRecipe) {
        DepartmentService departmentService = BasicAPI.getService(DepartmentService.class);
        if(null == nowRecipe.getDepart()){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定科室id为空.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定科室id为空");
            return;
        }
        DepartmentDTO department = departmentService.get(nowRecipe.getDepart());
        if(null == department){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定科室不存在.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定科室不存在");
            return;
        }
        sendYtRecipe.setCategory(department.getName());
    }

    /**
     * @method  assemblePatientMsg
     * @description 组装推送的处方单中患者的信息
     * @date: 2019/7/10
     * @author: JRK
     * @param sendYtRecipe 推送的处方单信息
     * @param nowRecipe 当前处方
     */
    private void assemblePatientMsg(DrugEnterpriseResult result, YtRecipeDTO sendYtRecipe, Recipe nowRecipe) {
        PatientService patientService = BasicAPI.getService(PatientService.class);
        if(null == nowRecipe.getMpiid()){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定患者id为空.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定患者id为空");
            return;
        }
        PatientDTO patient = patientService.get(nowRecipe.getMpiid());
        if(null == patient){
            LOGGER.warn("YtRemoteService.pushRecipeInfo:处方ID为{},绑定患者不存在.", nowRecipe.getRecipeId());
            getFailResult(result, "处方绑定患者不存在");
            return;
        }
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrder order = recipeOrderDAO.getByOrderCode(nowRecipe.getOrderCode());
        sendYtRecipe.setPatientName(patient.getPatientName());
        sendYtRecipe.setSex(Integer.parseInt(patient.getPatientSex()));
        sendYtRecipe.setAge(patient.getAge());
        sendYtRecipe.setPhone(patient.getMobile());
        sendYtRecipe.setSymptom(patient.getLastSummary());
        sendYtRecipe.setRecipientName(order.getReceiver());
        if (nowRecipe.getGiveMode() == 1) {
            sendYtRecipe.setRecipientAdd(getCompleteAddress(order));
            sendYtRecipe.setAddress(patient.getAddress());
        }
        sendYtRecipe.setRecipientTel(order.getRecMobile());
        sendYtRecipe.setZipCode("");
    }

    /**
     * @method  checkDrugListByDeil
     * @description 校验某一家药店下的药品是否符合库存量
     * @date: 2019/7/11
     * @author: JRK
     * @param drugGroup 处方详情的药品分组
     * @param drugsEnterprise 药企
     * @param saleDrug 销售药品
     * @param result 结果集
     * @param pharmacy 药店
       * @param sumFlag 库存检查结果
     * @return GroupSumResult 放回的药店下药品检查结果
    */
    private GroupSumResult checkDrugListByDeil(Map<Integer, DetailDrugGroup> drugGroup, DrugsEnterprise drugsEnterprise, SaleDrugList saleDrug, DrugEnterpriseResult result, Pharmacy pharmacy, boolean sumFlag, Integer recipeId) {
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        GroupSumResult groupSumResult = new GroupSumResult();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        for (Map.Entry<Integer, DetailDrugGroup> entry : drugGroup.entrySet()) {
            //发送请求访问药品的库存
            CloseableHttpClient httpClient = HttpClients.createDefault();
            try {
                if (drugsEnterprise.getBusinessUrl().contains("http:")) {
                    //拼接查询url
                    saleDrug = saleDrugListDAO.getByDrugIdAndOrganId(entry.getKey(), drugsEnterprise.getId());
                    if(null == saleDrug){
                        LOGGER.warn("YtRemoteService.pushRecipeInfo:处方细节ID为{},对应销售药品的信息不存在", entry.getValue().getRecipeDetailId());
                        getFailResult(result, "销售药品的信息不存在");
                        break;
                    }
                    CloseableHttpResponse response = sendStockHttpRequest(drugsEnterprise, saleDrug, pharmacy, httpClient);
                    //当相应状态为200时返回json
                    HttpEntity httpEntity = response.getEntity();
                    String responseStr = EntityUtils.toString(httpEntity);
                    LOGGER.info("YtRemoteService.scanStock.responseStr:{}", responseStr);
                    if(CommonConstant.requestSuccessCode == response.getStatusLine().getStatusCode()){
                        YtStockResponse stockResponse = JSONUtils.parse(responseStr, YtStockResponse.class);
                        LOGGER.info("YtRemoteService.scanStock:[{}]门店该[{}]药品查询库存，请求返回:{}", pharmacy.getPharmacyCode(), saleDrug.getOrganDrugCode(), responseStr);
                        //增加英特药企库存日志记录
                        try{
                            Recipe recipe = recipeDAO.getByRecipeId(recipeId);
                            String msg = "药企:"+drugsEnterprise.getName()+",药品名称:" + saleDrug.getDrugName() + ",药品库存:" + stockResponse.getStock() + ",平台药品编码:"+saleDrug.getDrugId() + ",药店编码:"+stockResponse.getCode()+",处方单号:"+recipeId;
                            LOGGER.info("YtRemoteService.scanStock:{}", msg);
                            if (recipe != null && recipe.getStatus() == 0) {
                                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), msg);
                            }
                        }catch(Exception e){
                            LOGGER.error("YtRemoteService.checkDrugListByDeil error:{},{}.", recipeId, e.getMessage(),e);
                        }
                        if(entry.getValue().getSumUsage() <= stockResponse.getStock()){
                            groupSumResult.setComplacentNum(groupSumResult.getComplacentNum() + 1);
                            if(sumFlag){
                                groupSumResult.setFeeSum(groupSumResult.getFeeSum() + stockResponse.getPrice() * entry.getValue().getSumUsage());
                            }
                        }else{
                            break;
                        }
                    }else{
                        LOGGER.warn("YtRemoteService.scanStock:[{}]门店该[{}]药品查询库存失败,失败原因:{}", pharmacy.getPharmacyCode(), saleDrug.getOrganDrugCode(), responseStr);
                        break;
                    }

                }
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("YtRemoteService.scanStock:[{}]门店该[{}]药品查询库存异常：{}", pharmacy.getPharmacyCode(), saleDrug.getOrganDrugCode(), e.getMessage(),e);
            } finally {
                try {
                    httpClient.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    LOGGER.error("YtRemoteService.scanStock:http请求资源关闭异常: {}", e.getMessage(),e);
                }
            }
        }
        return groupSumResult;
    }

    /**
     * @method  sendStockHttpRequest
     * @description 发送药店下药品信息的请求
     * @date: 2019/7/11
     * @author: JRK
     * @param drugsEnterprise
     * @param saleDrug 销售药品
     * @param pharmacy 药店
     * @param httpClient 请求服务
     * @return org.apache.http.client.methods.CloseableHttpResponse
     * 返回药店下药品信息的响应
     */
    private CloseableHttpResponse sendStockHttpRequest(DrugsEnterprise drugsEnterprise, SaleDrugList saleDrug, Pharmacy pharmacy, CloseableHttpClient httpClient) throws IOException {
        HttpGet httpGet = new HttpGet(drugsEnterprise.getBusinessUrl() +
                getStock + "/" + pharmacy.getPharmacyCode() + "/" + saleDrug.getOrganDrugCode());
        LOGGER.info("YtRemoteService.sendStockHttpRequest url:{}.", drugsEnterprise.getBusinessUrl() +
                getStock + "/" + pharmacy.getPharmacyCode() + "/" + saleDrug.getOrganDrugCode());
        //组装请求参数(组装权限验证部分)
        httpGet.setHeader(requestHeadJsonKey, requestHeadJsonValue);
        httpGet.setHeader(requestHeadPowerKey, drugsEnterprise.getToken());
        LOGGER.info("YtRemoteService.sendStockHttpRequest:[{}]门店该[{}]药品发送查询库存请求", pharmacy.getPharmacyCode(), saleDrug.getOrganDrugCode());

        //获取响应消息
        return httpClient.execute(httpGet);
    }

    /**
     * @method  getDetailGroup
     * @description 将处方详情中的根据药品分组，叠加需求量
     * @date: 2019/7/10
     * @author: JRK
     * @param detailList 处方详情
     * @return java.util.Map<java.lang.Integer,recipe.drugsenterprise.bean.DetailDrugGroup>
     *     详情药品需求量分组
     */
    private Map<Integer, DetailDrugGroup> getDetailGroup(List<Recipedetail> detailList) {
        Map<Integer, DetailDrugGroup> result = new HashMap<>();
        DetailDrugGroup nowDetailDrugGroup;
        //遍历处方详情，通过drugId判断，相同药品下的需求量叠加
        for (Recipedetail recipedetail : detailList) {
            nowDetailDrugGroup = result.get(recipedetail.getDrugId());
            if(null == nowDetailDrugGroup){
                DetailDrugGroup newDetailDrugGroup = new DetailDrugGroup();
                result.put(recipedetail.getDrugId(), newDetailDrugGroup);
                newDetailDrugGroup.setDrugId(recipedetail.getDrugId());
                newDetailDrugGroup.setRecipeDetailId(recipedetail.getRecipeDetailId());
                newDetailDrugGroup.setSumUsage(recipedetail.getUseTotalDose());
            }else{
                //叠加需求量
                nowDetailDrugGroup.setSumUsage(nowDetailDrugGroup.getSumUsage()+ recipedetail.getUseTotalDose());
                result.put(recipedetail.getDrugId(), nowDetailDrugGroup);
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
    @RpcService
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        //首先根据你筛选的距离获取药企下的所有药店
        List<Pharmacy> pharmacyList = getPharmacies(recipeIds, ext, enterprise, result);
        if(DrugEnterpriseResult.FAIL.equals(result.getCode())){
            return result;
        }

        //根据当前处方，对应药店下的处方的药品是否有库存
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        //现在默认只有一个处方单
        List<Recipedetail> detailList = detailDAO.findByRecipeId(recipeIds.get(0));
        if(CollectionUtils.isEmpty(detailList)){
            LOGGER.warn("YtRemoteService.findSupportDep:处方单{}的细节信息为空", recipeIds.get(0));
            getFailResult(result, "处方单细节信息为空");
            return result;
        }
        Map<Integer, DetailDrugGroup> drugGroup = getDetailGroup(detailList);
        Map<Integer, BigDecimal> feeSumByPharmacyIdMap = new HashMap<>();
        //删除库存不够的药店
        removeNoStockPhamacy(enterprise, result, pharmacyList, drugGroup, feeSumByPharmacyIdMap, recipeIds.get(0));

        //数据封装成页面展示数据
        List<DepDetailBean> pharmacyDetailPage = assemblePharmacyPageMsg(ext, enterprise, pharmacyList, feeSumByPharmacyIdMap);
        result.setObject(pharmacyDetailPage);
        return result;
    }

    /**
     * @method  removeNoStockPhamacy
     * @description 移除库存不够的
     * @date: 2019/7/22
     * @author: JRK
     * @param enterprise 药企
     * @param result 结果集
     * @param pharmacyList 药店列表
     * @param drugGroup 处方药品分组
     * @param feeSumByPharmacyIdMap 符合库存的药店下对应处方药品下的总价
     * @return void
     */
    private void removeNoStockPhamacy(DrugsEnterprise enterprise, DrugEnterpriseResult result, List<Pharmacy> pharmacyList, Map<Integer, DetailDrugGroup> drugGroup, Map<Integer, BigDecimal> feeSumByPharmacyIdMap, Integer recipeId) {
        Iterator<Pharmacy> iterator = pharmacyList.iterator();
        Pharmacy next;
        SaleDrugList saleDrug = null;
        while (iterator.hasNext()) {
            next = iterator.next();
            //判断药店库存
            GroupSumResult groupSumResult = checkDrugListByDeil(drugGroup, enterprise, saleDrug, result, next, true, recipeId);
            //不够的移除
            if(groupSumResult.getComplacentNum() < drugGroup.size()){
                iterator.remove();
            }else{
                feeSumByPharmacyIdMap.put(next.getPharmacyId(), new BigDecimal(Double.toString(groupSumResult.getFeeSum())));
            }
        }
    }

    /**
     * @method  getPharmacies
     * @description 获取搜索信息下的药店
     * @date: 2019/7/12
     * @author: JRK
     * @param recipeIds 处方id集合
     * @param ext 搜索条件
     * @param enterprise 药企
     * @param result 结果集
     * @return java.util.List<com.ngari.recipe.entity.Pharmacy>搜素信息下的药店列表
     */
    private List<Pharmacy> getPharmacies(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise, DrugEnterpriseResult result) {
        PharmacyDAO pharmacyDAO = DAOFactory.getDAO(PharmacyDAO.class);
        List<Pharmacy> pharmacyList = new ArrayList<Pharmacy>();
        if (ext != null && null != ext.get(searchMapRANGE) && null != ext.get(searchMapLongitude) && null != ext.get(searchMapLatitude)) {
            pharmacyList = pharmacyDAO.findByDrugsenterpriseIdAndStatusAndRangeAndLongitudeAndLatitude(enterprise.getId(), Double.parseDouble(ext.get(searchMapRANGE).toString()), Double.parseDouble(ext.get(searchMapLongitude).toString()), Double.parseDouble(ext.get(searchMapLatitude).toString()));
        }else{
            LOGGER.warn("YtRemoteService.findSupportDep:请求的搜索参数不健全" );
            getFailResult(result, "请求的搜索参数不健全");
        }
        if(CollectionUtils.isEmpty(recipeIds)){
            LOGGER.warn("YtRemoteService.findSupportDep:查询的处方单为空" );
            getFailResult(result, "查询的处方单为空");
        }
        return pharmacyList;
    }

    /**
     * @method  assemblePharmacyPageMsg
     * @description 组装页面展示的药店信息
     * @date: 2019/7/12
     * @author: JRK
     * @param ext 搜索map
     * @param enterprise 药企
     * @param pharmacyList 药店列表
     * @param feeSumByPharmacyIdMap 药店对应的费用
     * @return java.util.List<com.ngari.recipe.drugsenterprise.model.DepDetailBean> 页面药店信息展示
     */
    private List<DepDetailBean> assemblePharmacyPageMsg(Map ext, DrugsEnterprise enterprise, List<Pharmacy> pharmacyList, Map<Integer, BigDecimal> feeSumByPharmacyIdMap) {
       List<DepDetailBean> pharmacyDetailPage = new ArrayList<>();
        SaleDrugList checkSaleDrug = null;
        Position position;
        DepDetailBean newDepDetailBean;
        for (Pharmacy pharmacyMsg : pharmacyList) {
            newDepDetailBean = new DepDetailBean();
            pharmacyDetailPage.add(newDepDetailBean);
            newDepDetailBean.setDepId(enterprise.getId());
            newDepDetailBean.setDepName(pharmacyMsg.getPharmacyName());
            //根据药店信息获取上面跌加出的总价格
            newDepDetailBean.setRecipeFee(feeSumByPharmacyIdMap.get(pharmacyMsg.getPharmacyId()));
            newDepDetailBean.setSendMethod(ytSendMethod);
            newDepDetailBean.setPayMethod(ytPayMethod);
            newDepDetailBean.setPharmacyCode(pharmacyMsg.getPharmacyCode());
            newDepDetailBean.setAddress(pharmacyMsg.getPharmacyAddress());
            position = new Position();
            position.setLatitude(Double.parseDouble(pharmacyMsg.getPharmacyLatitude()));
            position.setLongitude(Double.parseDouble(pharmacyMsg.getPharmacyLongitude()));
            position.setRange(Integer.parseInt(ext.get(searchMapRANGE).toString()));
            newDepDetailBean.setPosition(position);
            newDepDetailBean.setBelongDepName(enterprise.getName());
            //记录药店和用户两个经纬度的距离
            newDepDetailBean.setDistance(DistanceUtil.getDistance(Double.parseDouble(ext.get(searchMapLatitude).toString()),
                    Double.parseDouble(ext.get(searchMapLongitude).toString()), Double.parseDouble(pharmacyMsg.getPharmacyLatitude()), Double.parseDouble(pharmacyMsg.getPharmacyLongitude())));
        }
        return pharmacyDetailPage;
    }

    public String getDrugEnterpriseCallSys() {
        return DrugEnterpriseConstant.COMPANY_YT;
    }

    public boolean scanStockSend(Integer recipeId, DrugsEnterprise drugsEnterprise){
        LOGGER.warn("scanStockSend校验英特库存处方{}请求中", recipeId);
        boolean checkScan =false;

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe nowRecipe = recipeDAO.get(recipeId);
        if(null == nowRecipe){
            LOGGER.warn("scanStockSend校验英特库存：当前处方不存在{}", recipeId);
            return checkScan;
        }

        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Recipedetail> detailList = detailDAO.findByRecipeId(nowRecipe.getRecipeId());
        Map<Integer, DetailDrugGroup> drugGroup = getDetailGroup(detailList);
        if(0 >= drugGroup.size()){
            LOGGER.warn("scanStockSend校验英特库存：当前处方药品信息不存在{}", recipeId);
            return checkScan;
        }

        RecipeParameterDao recipeParameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        //根据机构_yt_store_code获取配送药店
        Integer organId = nowRecipe.getClinicOrgan();
        String store_code = organId + "_" + "yt_store_code";
        String storeCode = recipeParameterDao.getByName(store_code);
        if(StringUtils.isNotEmpty(storeCode)){
            LOGGER.info("scanStockSend校验英特库存：当前医院配置配送药企code{}，处方id{}", storeCode, recipeId);
            Pharmacy pharmacy = new Pharmacy();
            pharmacy.setPharmacyCode(storeCode);
            GroupSumResult groupSumResult = checkDrugListByDeil(drugGroup, drugsEnterprise, null, DrugEnterpriseResult.getSuccess(), pharmacy, false, recipeId);
            //只有当某一家药店有所有处方详情下的药品并且库存不超过，查询库存的结果设为成功
            if(groupSumResult.getComplacentNum() >= drugGroup.size()){
                checkScan = true;
            }
        }else{
            LOGGER.info("scanStockSend校验英特库存：当前医院没有配置配送药企{}", organId);
        }
        return checkScan;
    }
}