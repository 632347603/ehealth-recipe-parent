package recipe.drugsenterprise;

import com.alibaba.fastjson.JSONObject;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.OrganService;
import com.ngari.recipe.drugsenterprise.model.DepDetailBean;
import com.ngari.recipe.drugsenterprise.model.Position;
import com.ngari.recipe.entity.DrugsEnterprise;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.Recipedetail;
import com.ngari.recipe.entity.SaleDrugList;
import com.ngari.recipe.hisprescription.model.HospitalRecipeDTO;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.bean.DrugEnterpriseResult;
import recipe.constant.DrugEnterpriseConstant;
import recipe.dao.*;
import recipe.drugsenterprise.bean.HdDrugRequestData;
import recipe.drugsenterprise.bean.HdPosition;
import recipe.drugsenterprise.bean.ScanStockBean;
import recipe.drugsenterprise.bean.YnsPharmacyAndStockRequest;
import recipe.drugsenterprise.bean.yd.httpclient.HttpsClientUtils;
import recipe.util.MapValueUtil;

import java.util.*;

/**
 * @Description: YnsRemoteService 类（或接口）是 对接昆明市第一人民医院医药服务接口
 * @Author: JRK
 * @Date: 2019/7/8
 */
@RpcBean("kmsRemoteService")
public class KmsRemoteService extends AccessDrugEnterpriseService {

    private static final Logger LOGGER = LoggerFactory.getLogger(KmsRemoteService.class);

    private static final String searchMapRANGE = "range";
    private static final String searchMapLatitude = "latitude";
    private static final String searchMapLongitude = "longitude";
    private static final String drugRecipeTotal = "0";
    private static final String requestSuccessCode = "000";

    @Override
    public void tokenUpdateImpl(DrugsEnterprise drugsEnterprise) {

    }

    @Override
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult pushRecipe(HospitalRecipeDTO hospitalRecipeDTO, DrugsEnterprise enterprise) {
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public String getDrugInventory(Integer drugId, DrugsEnterprise drugsEnterprise, Integer organId) {
        return null;
    }

    @RpcService
    public void test(Integer recipeId) {
        List<Integer> recipeIds = Arrays.asList(recipeId);
        DrugsEnterpriseDAO enterpriseDAO = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        DrugsEnterprise drugsEnterprise = enterpriseDAO.getById(240);
        Map ext = new HashMap();
        ext.put("longitude", "102.6");
        ext.put("latitude", "25.1");
        ext.put("range", "100");
        findSupportDep(recipeIds, ext, drugsEnterprise);
//        scanStock(recipeId, drugsEnterprise);
    }

    @Override
    public DrugEnterpriseResult scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
        LOGGER.info("KmsRemoteService.scanStock:[{}]", JSONUtils.toString(recipeId));
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        RecipeParameterDao recipeParameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        String goodsqtyMethod = recipeParameterDao.getByName("kms-goodsqty");
        //发送请求，获得推送的结果
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            //查询当前处方信息
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            Recipe nowRecipe = recipeDAO.get(recipeId);
            //查询当前处方下详情信息
            RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
            List<Recipedetail> detailList = detailDAO.findByRecipeId(nowRecipe.getRecipeId());

            //根据药品请求华东旗下的所有可用药店，当有一个可用说明库存是足够的
            Map<String, HdDrugRequestData> drugResultMap = new HashMap<>();
            List<HdDrugRequestData> drugRequestDataList = getDrugRequestList(drugResultMap, detailList, drugsEnterprise, result);
            if (DrugEnterpriseResult.FAIL == result.getCode()) return result;
            Map<String, Object> requestMap = new HashMap<>();
            requestMap.put("drugList", drugRequestDataList);
            String requestStr = JSONUtils.toString(requestMap);
            ////根据处方信息发送药企库存查询请求，判断有药店是否满足库存
            LOGGER.info("KmsRemoteService.scanStock:[{}][{}]根据处方信息发送药企库存查询请求，请求内容：{}", drugsEnterprise.getId(), drugsEnterprise.getName(), requestStr);
            String outputData = HttpsClientUtils.doPost(drugsEnterprise.getBusinessUrl() + goodsqtyMethod, requestStr);
            //获取响应消息
            LOGGER.info("KmsRemoteService.scanStock:[{}][{}]同步药品请求，获取响应消息：{}", drugsEnterprise.getId(), drugsEnterprise.getName(), JSONUtils.toString(outputData));

            Map resultMap = JSONUtils.parse(outputData, Map.class);
            if (requestSuccessCode.equals(MapValueUtil.getString(resultMap, "code"))) {
                List<Map<String, Object>> drugList = MapValueUtil.getList(resultMap, "drugList");
                if (CollectionUtils.isNotEmpty(drugList) && drugList.size() > 0) {
                    for (Map<String, Object> drugBean : drugList) {
                        String inventory = MapValueUtil.getObject(drugBean, "inventory").toString();
                        if ("false".equals(inventory)) {
                            getFailResult(result, "当前药企下没有药店的药品库存足够");
                            return result;
                        }
                    }
                    LOGGER.info("KmsRemoteService.findSupportDep:[{}][{}]获取药品库存请求，返回前端result消息：{}", drugsEnterprise.getId(), drugsEnterprise.getName(), JSONUtils.toString(result));
                } else {
                    getFailResult(result, "当前药企下没有药店的药品库存足够");
                }

            } else {
                getFailResult(result, "当前药企下没有药店的药品库存足够");
            }
            LOGGER.info("KmsRemoteService-scanStock 药店取药药品库存:{}.", JSONUtils.toString(result.getObject()));
        } catch (Exception e) {
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setMsg(e.getMessage());
            LOGGER.error("KmsRemoteService.scanStock:[{}][{}]获取药品库存异常：{}", drugsEnterprise.getId(), drugsEnterprise.getName(), e.getMessage());
            getFailResult(result, e.getMessage());
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("KmsRemoteService.scanStock:http请求资源关闭异常: {}！", e.getMessage());
            }
        }
        return result;
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
        LOGGER.info("KmsRemoteService.findSupportDep:[{}]", JSONUtils.toString(recipeIds));
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        RecipeParameterDao recipeParameterDao = DAOFactory.getDAO(RecipeParameterDao.class);
        String placepointMethod = recipeParameterDao.getByName("kms-placepoint");
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        //发送请求，获得推送的结果
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            List<Recipedetail> detailList = detailDAO.findByRecipeId(recipeIds.get(0));
            RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
            if (CollectionUtils.isEmpty(detailList)) {
                LOGGER.warn("KmsRemoteService.findSupportDep:处方单{}的细节信息为空", recipeIds.get(0));

            }
            Recipe nowRecipe = recipeDAO.get(recipeIds.get(0));
            if (null == nowRecipe) {
                LOGGER.warn("KmsRemoteService.findSupportDep:处方单{}不存在", recipeIds.get(0));
            }
            OrganService organService = BasicAPI.getService(OrganService.class);
            OrganDTO organ = organService.getByOrganId(nowRecipe.getClinicOrgan());
            if (null == organ) {
                LOGGER.warn("KmsRemoteService.findSupportDep:处方ID为{},对应的开处方机构不存在.", nowRecipe.getRecipeId());
            }
            //根据请求返回的药店列表，获取对应药店下药品需求的总价格
            Map<String, HdDrugRequestData> drugResultMap = new HashMap<>();
            //首先组装请求对象
            YnsPharmacyAndStockRequest hdPharmacyAndStockRequest = new YnsPharmacyAndStockRequest();
            if (ext != null && null != ext.get(searchMapRANGE) && null != ext.get(searchMapLongitude) && null != ext.get(searchMapLatitude)) {
                List<HdDrugRequestData> drugRequestList = getDrugRequestList(drugResultMap, detailList, enterprise, result);
                if (DrugEnterpriseResult.FAIL == result.getCode()) return result;
                hdPharmacyAndStockRequest.setDrugList(drugRequestList);
                hdPharmacyAndStockRequest.setRange("20");
                hdPharmacyAndStockRequest.setPosition(new HdPosition(MapValueUtil.getString(ext, searchMapLongitude), MapValueUtil.getString(ext, searchMapLatitude)));

            } else {
                LOGGER.warn("KmsRemoteService.findSupportDep:请求的搜索参数不健全");
            }
            if (DrugEnterpriseResult.FAIL == result.getCode()) return result;

            String requestStr = JSONUtils.toString(hdPharmacyAndStockRequest);
            ////根据处方信息发送药企药店列表查询请求，判断是否企药店列表
            LOGGER.info("KmsRemoteService.findSupportDep:[{}][{}]根据查询药店列表请求，请求内容：{}", enterprise.getId(), enterprise.getName(), requestStr);
            String outputData = HttpsClientUtils.doPost(enterprise.getBusinessUrl() + placepointMethod, requestStr);
            //获取响应消息
            LOGGER.info("KmsRemoteService.findSupportDep:[{}][{}]查询药店列表请求，获取响应消息：{}", enterprise.getId(), enterprise.getName(), JSONUtils.toString(outputData));

            Map resultMap = JSONUtils.parse(outputData, Map.class);
            if (requestSuccessCode.equals(MapValueUtil.getString(resultMap, "code"))) {
                //接口返回的结果
                List<Map<String, Object>> ynsStoreBeans = MapValueUtil.getList(resultMap, "list");
                //数据封装成页面展示数据
                List<DepDetailBean> list = new ArrayList<>();
                DepDetailBean detailBean;
                for (Map<String, Object> ynsStoreBean : ynsStoreBeans) {
                    LOGGER.info("KmsRemoteService.findSupportDep ynsStoreBean:{}.", JSONUtils.toString(ynsStoreBean));
                    detailBean = new DepDetailBean();
                    detailBean.setPharmacyCode(MapValueUtil.getString(ynsStoreBean, "pharmacyCode"));
                    detailBean.setDepName(MapValueUtil.getString(ynsStoreBean, "pharmacyName"));
                    detailBean.setAddress(MapValueUtil.getString(ynsStoreBean, "address"));
                    detailBean.setDistance(Double.parseDouble(MapValueUtil.getString(ynsStoreBean, "distance")));
                    LOGGER.info("KmsRemoteService.findSupportDep pharmacyCode:{}.", MapValueUtil.getString(ynsStoreBean, "pharmacyCode"));
                    Map position = (Map) MapValueUtil.getObject(ynsStoreBean, "position");
                    LOGGER.info("KmsRemoteService.findSupportDep position:{}.", position);
                    //Map mp = JSONUtils.parse(position, Map.class);
                    LOGGER.info("KmsRemoteService.findSupportDep mp:{}.", JSONUtils.toString(position));
                    Position postion = new Position();
                    postion.setLatitude(Double.parseDouble(MapValueUtil.getString(position, "latitude")));
                    postion.setLongitude(Double.parseDouble(MapValueUtil.getString(position, "longitude")));
                    detailBean.setPosition(postion);
                    list.add(detailBean);
                }
                result.setObject(list);
                LOGGER.info("KmsRemoteService.findSupportDep:[{}][{}]查询药店列表请求，返回前端result消息：{}", enterprise.getId(), enterprise.getName(), JSONUtils.toString(result));
            } else {
                getFailResult(result, "当前药企下没有药店列表");
            }
            LOGGER.info("HdRemoteService-scanStock 返回药店列表:{}.", JSONUtils.toString(result.getObject()));
        } catch (Exception e) {
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setMsg(e.getMessage());
            LOGGER.error("KmsRemoteService.scanStock:[{}][{}]获取药品库存异常：{}", enterprise.getId(), enterprise.getName(), e.getMessage());
            getFailResult(result, e.getMessage());
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                e.printStackTrace();
                LOGGER.error("KmsRemoteService.scanStock:http请求资源关闭异常: {}！", e.getMessage());
            }
        }
        return result;
    }

    /**
     * @param detailList  处方下的详情列表
     * @param finalResult 请求的最终结果
     * @return java.util.List<recipe.drugsenterprise.bean.HdDrugRequestData>请求药店下药品信息列表
     * @method getDrugRequestList
     * @description 获取请求药店下药品信息接口下的药品总量（根据药品的code分组）的list
     * @date: 2019/7/25
     * @author: JRK
     */
    private List<HdDrugRequestData> getDrugRequestList(Map<String, HdDrugRequestData> result, List<Recipedetail> detailList, DrugsEnterprise drugsEnterprise, DrugEnterpriseResult finalResult) {
        LOGGER.info("KmsRemoteService.getDrugRequestList:[{}][{}]获取请求药店下药品信息接口下的药品总量（根据药品的code分组）的list：{}", drugsEnterprise.getId(), drugsEnterprise.getName(), JSONUtils.toString(detailList));
        HdDrugRequestData hdDrugRequestData;
        Double sum;
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        SaleDrugList saleDrug;
        //遍历处方详情，通过drugId判断，相同药品下的需求量叠加
        for (Recipedetail recipedetail : detailList) {
            //这里的药品是对接到药企下的所以取的是saleDrugList的药品标识
            saleDrug = saleDrugListDAO.getByDrugIdAndOrganId(recipedetail.getDrugId(), drugsEnterprise.getId());
            if (null == saleDrug) {
                LOGGER.warn("KmsRemoteService.pushRecipeInfo:药品id:{},药企id:{}的药企药品信息不存在",
                        recipedetail.getDrugId(), drugsEnterprise.getId());
                getFailResult(finalResult, "对接的药品信息为空");
                return new ArrayList<>();
            }
            hdDrugRequestData = result.get(saleDrug.getOrganDrugCode());

            if (null == hdDrugRequestData) {
                HdDrugRequestData newHdDrugRequestData1 = new HdDrugRequestData();
                result.put(saleDrug.getOrganDrugCode(), newHdDrugRequestData1);
                newHdDrugRequestData1.setDrugCode(saleDrug.getOrganDrugCode());
                newHdDrugRequestData1.setTotal(null == recipedetail.getUseTotalDose() ? drugRecipeTotal : String.valueOf(new Double(recipedetail.getUseTotalDose()).intValue()));
                newHdDrugRequestData1.setUnit(recipedetail.getDrugUnit());
            } else {
                //叠加需求量
                sum = Double.parseDouble(hdDrugRequestData.getTotal()) + recipedetail.getUseTotalDose();
                hdDrugRequestData.setTotal(String.valueOf(sum.intValue()));
                result.put(saleDrug.getOrganDrugCode(), hdDrugRequestData);
            }
        }
        //将叠加好总量的药品分组转成list
        List<HdDrugRequestData> hdDrugRequestDataList = new ArrayList<HdDrugRequestData>(result.values());
        return hdDrugRequestDataList;
    }

    /**
     * @param result 返回的结果集对象
     * @param msg    失败提示的信息
     * @return
     * @method getFailResult
     * @description 失败操作的结果对象
     * @date: 2019/7/10
     * @author: JRK
     */
    private void getFailResult(DrugEnterpriseResult result, String msg) {
        result.setMsg(msg);
        result.setCode(DrugEnterpriseResult.FAIL);
    }

    @Override
    public String getDrugEnterpriseCallSys() {
        return DrugEnterpriseConstant.COMPANY_KMS;
    }

}