package recipe.drugsenterprise.commonExtendCompatible;

import com.google.common.base.Joiner;
import com.ngari.his.recipe.mode.DrugInfoResponseTO;
import com.ngari.his.recipe.mode.DrugInfoTO;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.drugsenterprise.model.DepDetailBean;
import com.ngari.recipe.drugsenterprise.model.DrugsDataBean;
import com.ngari.recipe.drugsenterprise.model.Position;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.constant.RecipeSendTypeEnum;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.ApplicationUtils;
import recipe.bean.DrugEnterpriseResult;
import recipe.bean.RecipePayModeSupportBean;
import recipe.constant.RecipeBussConstant;
import recipe.dao.*;
import recipe.drugsenterprise.AccessDrugEnterpriseService;
import recipe.hisservice.RecipeToHisService;
import recipe.service.DrugListExtService;
import recipe.service.RecipeHisService;
import recipe.util.DistanceUtil;
import recipe.util.MapValueUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service("commonSelfEnterprisesType")
public class CommonSelfEnterprisesType implements CommonExtendEnterprisesInterface{
    private static final String searchMapRANGE = "range";

    private static final String searchMapLatitude = "latitude";

    private static final String searchMapLongitude = "longitude";

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonSelfEnterprisesType.class);

    @Autowired
    private DrugListExtService drugListExtService;

    @Override
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        LOGGER.info("PublicSelfRemoteService pushRecipeInfo not implement.");
        //date 2019/12/4
        //添加自建药企推送处方时推送消息给药企
        AccessDrugEnterpriseService.pushMessageToEnterprise(recipeIds);
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    @RpcService
    public DrugEnterpriseResult scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
//        LOGGER.info("PublicSelfRemoteService scanStock not implement.");
//        return DrugEnterpriseResult.getSuccess();

        //date 20200525
        //判断库存是否足够，如果是配送主体是医院取药的，通过医院库存接口判断库存是否足够
        if(null == recipeId){
            LOGGER.warn("判断当前处方库存是否足够，处方id为空，校验失败！");
            return DrugEnterpriseResult.getFail();
        }
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if(null == recipe){
            LOGGER.warn("判断当前处方{}不存在，校验失败！", recipeId);
            return DrugEnterpriseResult.getFail();
        }
        if(null == drugsEnterprise){
            LOGGER.warn("判断当前处方{}查询药企不存在，校验失败！", recipeId);
            return DrugEnterpriseResult.getFail();
        }
        //date 20200603
        //这个自建药企的判断限定于平台，互联网的处方设置成有库存
        if((RecipeBussConstant.RECIPEMODE_ZJJGPT).equals(recipe.getRecipeMode())){
            LOGGER.warn("判断当前处方{}，当前处方为互联网处方，默认有库存！", recipeId);
            return DrugEnterpriseResult.getSuccess();
        }
        //配送主体类型 1医院配送 2 药企配送
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
        if(RecipeSendTypeEnum.ALRAEDY_PAY.getSendType() == drugsEnterprise.getSendType()){
            //当前医院配送，调用医院库存
            //当前医院呢库存接口，前置机对接了，则按对接的算
            //前置机没对接算库存足够
            RecipeResultBean scanResult = hisService.scanDrugStockByRecipeId(recipeId);
            if(null != scanResult){
                if(RecipeResultBean.SUCCESS == scanResult.getCode()){
                    LOGGER.warn("当前处方{}调用医院库存，库存足够", recipeId);
                    return DrugEnterpriseResult.getSuccess();
                }else{
                    LOGGER.warn("当前处方{}调用医院库存，库存不足", recipeId);
                    return DrugEnterpriseResult.getFail();
                }
            }else{
                LOGGER.warn("当前处方{}调用医院库存，返回为空，默认无库存", recipeId);
                return DrugEnterpriseResult.getFail();
            }

        }else{
            //当前配送主体不是医院配送，默认库存足够
            return DrugEnterpriseResult.getSuccess();
        }


    }

    @Override
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise) {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        String longitude = MapValueUtil.getString(ext, searchMapLongitude);
        String latitude = MapValueUtil.getString(ext, searchMapLatitude);
        if (recipeIds == null) {
            return DrugEnterpriseResult.getFail();
        }
        Integer recipeId = recipeIds.get(0);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (recipe == null) {
            return DrugEnterpriseResult.getFail();
        }
        RecipeDetailDAO recipeDetailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        List<Integer> drugs = recipeDetailDAO.findDrugIdByRecipeId(recipeId);
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        List<SaleDrugList> saleDrugLists = saleDrugListDAO.findByOrganIdAndDrugIds(enterprise.getId(), drugs);
        if (CollectionUtils.isEmpty(saleDrugLists) || saleDrugLists.size() < drugs.size()) {
            return DrugEnterpriseResult.getFail();
        }
        if (longitude != null && latitude != null) {
            //药店取药
            List<Pharmacy> pharmacyList = getPharmacies(recipeIds, ext, enterprise, result);
            List<DepDetailBean> detailList = new ArrayList<>();
            DepDetailBean detailBean;
            for (Pharmacy pharmacy : pharmacyList) {
                detailBean = new DepDetailBean();
                detailBean.setDepId(enterprise.getId());
                detailBean.setDepName(pharmacy.getPharmacyName());
                detailBean.setRecipeFee(BigDecimal.ZERO);
                detailBean.setExpressFee(BigDecimal.ZERO);
                detailBean.setPharmacyCode(pharmacy.getPharmacyCode());
                detailBean.setAddress(pharmacy.getPharmacyAddress());
                Position position = new Position();
                position.setLatitude(Double.parseDouble(pharmacy.getPharmacyLatitude()));
                position.setLongitude(Double.parseDouble(pharmacy.getPharmacyLongitude()));
                //position.setRange(Integer.parseInt(ext.get(searchMapRANGE).toString()));
                detailBean.setPosition(position);
                detailBean.setBelongDepName(enterprise.getName());
                //记录药店和用户两个经纬度的距离
                detailBean.setDistance(DistanceUtil.getDistance(Double.parseDouble(ext.get(searchMapLatitude).toString()),
                        Double.parseDouble(ext.get(searchMapLongitude).toString()), Double.parseDouble(pharmacy.getPharmacyLatitude()), Double.parseDouble(pharmacy.getPharmacyLongitude())));
                detailList.add(detailBean);
            }
            result.setObject(detailList);
        }
        return result;
    }

    @Override
    public boolean scanStock(Recipe dbRecipe, DrugsEnterprise dep, List<Integer> drugIds) {
        return false;
    }

    @Override
    public String appEnterprise(RecipeOrder order) {
        return null;
    }

    @Override
    public BigDecimal orderToRecipeFee(RecipeOrder order, List<Integer> recipeIds, RecipePayModeSupportBean payModeSupport, BigDecimal recipeFee, Map<String, String> extInfo) {
        return null;
    }

    @Override
    public void setOrderEnterpriseMsg(Map<String, String> extInfo, RecipeOrder order) {

    }

    @Override
    public void checkRecipeGiveDeliveryMsg(RecipeBean recipeBean, Map<String, Object> map) {

    }

    @Override
    public void setEnterpriseMsgToOrder(RecipeOrder order, Integer depId, Map<String, String> extInfo) {

    }

    @Override
    public Boolean specialMakeDepList(DrugsEnterprise drugsEnterprise, Recipe dbRecipe) {
        return null;
    }

    @Override
    public void sendDeliveryMsgToHis(Integer recipeId) {

    }

    @Override
    public DrugEnterpriseResult sendMsgResultMap(Integer recipeId, Map<String, String> extInfo, DrugEnterpriseResult payResult) {
        return null;
    }

    private List<Pharmacy> getPharmacies(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise, DrugEnterpriseResult result) {
        PharmacyDAO pharmacyDAO = DAOFactory.getDAO(PharmacyDAO.class);
        List<Pharmacy> pharmacyList = new ArrayList<Pharmacy>();
        if (ext != null && null != ext.get(searchMapRANGE) && null != ext.get(searchMapLongitude) && null != ext.get(searchMapLatitude)) {
            pharmacyList = pharmacyDAO.findByDrugsenterpriseIdAndStatusAndRangeAndLongitudeAndLatitude(enterprise.getId(), Double.parseDouble(ext.get(searchMapRANGE).toString()), Double.parseDouble(ext.get(searchMapLongitude).toString()), Double.parseDouble(ext.get(searchMapLatitude).toString()));
        }else{
            LOGGER.warn("CommonSelfRemoteService.findSupportDep:请求的搜索参数不健全" );
            getFailResult(result, "请求的搜索参数不健全");
        }
        if(CollectionUtils.isEmpty(recipeIds)){
            LOGGER.warn("CommonSelfRemoteService.findSupportDep:查询的处方单为空" );
            getFailResult(result, "查询的处方单为空");
        }
        return pharmacyList;
    }

    private void getFailResult(DrugEnterpriseResult result, String msg) {
        result.setMsg(msg);
        result.setCode(DrugEnterpriseResult.FAIL);
    }

    @Override
    public String getDrugInventory(Integer drugId, DrugsEnterprise drugsEnterprise, Integer organId) {
        //自建药企查询医院库存
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        List<OrganDrugList> organDrugLists = organDrugListDAO.findByOrganIdAndDrugIds(organId, Arrays.asList(drugId));
        DrugInfoResponseTO response = drugListExtService.getHisDrugStock(organId, organDrugLists, null);
        if (null == response) {
            return "有库存";
        } else {
            if (Integer.valueOf(0).equals(response.getMsgCode())) {
                if (CollectionUtils.isEmpty(response.getData())){
                    return "有库存";
                }else {
                    List<DrugInfoTO> data = response.getData();
                    Double stockAmount = data.get(0).getStockAmount();
                    if (stockAmount != null){
                        return String.valueOf(stockAmount);
                    }else {
                        return "有库存";
                    }
                }
            }
        }
        return "无库存";
    }

    @Override
    public List<String> getDrugInventoryForApp(DrugsDataBean drugsDataBean, DrugsEnterprise drugsEnterprise, Integer flag) {
        SaleDrugListDAO saleDrugListDAO = DAOFactory.getDAO(SaleDrugListDAO.class);
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        List<String> result = new ArrayList<>();
        if(RecipeSendTypeEnum.ALRAEDY_PAY.getSendType() == drugsEnterprise.getSendType()){
            RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
            for (RecipeDetailBean recipeDetailBean : drugsDataBean.getRecipeDetailBeans()) {
                List<OrganDrugList> organDrugLists = organDrugListDAO.findByDrugIdAndOrganId(recipeDetailBean.getDrugId(), drugsDataBean.getOrganId());
                SaleDrugList saleDrugList = saleDrugListDAO.getByDrugIdAndOrganIdAndStatus(recipeDetailBean.getDrugId(), drugsEnterprise.getId());
                if (CollectionUtils.isNotEmpty(organDrugLists) && saleDrugList != null) {
                    OrganDrugList organDrugList = organDrugLists.get(0);
                    List<Recipedetail> recipedetails = new ArrayList<>();
                    Recipedetail recipedetail = ObjectCopyUtils.convert(recipeDetailBean, Recipedetail.class);
                    if (organDrugList != null) {
                        recipedetail.setPack(organDrugList.getPack());
                        recipedetail.setDrugUnit(organDrugList.getUnit());
                        recipedetail.setProducerCode(organDrugList.getProducerCode());
                        recipedetails.add(recipedetail);
                        DrugInfoResponseTO response = service.scanDrugStock(recipedetails, drugsDataBean.getOrganId());
                        if (response != null && Integer.valueOf(0).equals(response.getMsgCode())) {
                            //表示有库存
                            result.add(recipeDetailBean.getDrugName());
                        }
                    }
                }
            }
        } else {
            for (RecipeDetailBean recipeDetailBean : drugsDataBean.getRecipeDetailBeans()) {
                SaleDrugList saleDrugList = saleDrugListDAO.getByDrugIdAndOrganIdAndStatus(recipeDetailBean.getDrugId(), drugsEnterprise.getId());
                if (saleDrugList != null) {
                    result.add(recipeDetailBean.getDrugName());
                }
            }
        }
        return result;
    }


}