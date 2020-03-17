package recipe.drugsenterprise.compatible;

import com.alijk.bqhospital.alijk.conf.TaobaoConf;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.*;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.OrganService;
import com.ngari.recipe.entity.DrugsEnterprise;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.persistence.DAOFactory;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.bean.DrugEnterpriseResult;
import recipe.bean.RecipePayModeSupportBean;
import recipe.dao.*;
import recipe.drugsenterprise.AccessDrugEnterpriseService;
import recipe.drugsenterprise.CommonRemoteService;
import recipe.hisservice.RecipeToHisService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @description 杭州互联网（金投）对接服务旧实现方式
 * @author JRK
 * @date 2020/3/16
 */
@Service("hzInternetRemoteOldType")
public class HzInternetRemoteOldType implements HzInternetRemoteTypeInterface {
    @Override
    public Boolean specialMakeDepList(DrugsEnterprise drugsEnterprise, Recipe dbRecipe) {
        return false;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(HzInternetRemoteOldType.class);

    private static final String EXPIRE_TIP = "请重新授权";

    @Autowired
    private RecipeExtendDAO recipeExtendDAO;

    @Autowired
    private TaobaoConf taobaoConf;

    @Override
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        LOGGER.info("旧-杭州互联网虚拟药企-更新取药信息至处方流转平台开始，处方ID：{}.", JSONUtils.toString(recipeIds));
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        //1物流配送
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeIds.get(0));
        RecipeToHisService service = AppContextHolder.getBean("recipeToHisService", RecipeToHisService.class);
        OrganService organService = BasicAPI.getService(OrganService.class);


        UpdateTakeDrugWayReqTO updateTakeDrugWayReqTO = new UpdateTakeDrugWayReqTO();
        updateTakeDrugWayReqTO.setClinicOrgan(recipe.getClinicOrgan());
        //平台处方号
        updateTakeDrugWayReqTO.setNgarRecipeId(recipe.getRecipeId()+"");
        //医院处方号
        //流转到这里来的属于物流配送
        updateTakeDrugWayReqTO.setDeliveryType("1");
        updateTakeDrugWayReqTO.setRecipeID(recipe.getRecipeCode());
        updateTakeDrugWayReqTO.setOrganID(organService.getOrganizeCodeByOrganId(recipe.getClinicOrgan()));

        updateTakeDrugWayReqTO.setPayMode("1");
        RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
        if(recipeExtend != null && recipeExtend.getDeliveryCode() != null){
            updateTakeDrugWayReqTO.setDeliveryCode(recipeExtend.getDeliveryCode());
            updateTakeDrugWayReqTO.setDeliveryName(recipeExtend.getDeliveryName());
        } else {
            LOGGER.info("杭州互联网虚拟药企-未获取his返回的配送药-recipeId={}", JSONUtils.toString(recipe.getRecipeId()));
            result.setMsg("未获取his返回的配送药");
            result.setCode(DrugEnterpriseResult.FAIL);
        }
        if (StringUtils.isNotEmpty(recipe.getOrderCode())){
            RecipeOrderDAO dao = DAOFactory.getDAO(RecipeOrderDAO.class);
            RecipeOrder order = dao.getByOrderCode(recipe.getOrderCode());
            if (order!=null){
                //收货人
                updateTakeDrugWayReqTO.setConsignee(order.getReceiver());
                //联系电话
                updateTakeDrugWayReqTO.setContactTel(order.getRecTel());
                //详细收货地址
                CommonRemoteService commonRemoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
                updateTakeDrugWayReqTO.setAddress(commonRemoteService.getCompleteAddress(order));

                //收货地址代码
                updateTakeDrugWayReqTO.setReceiveAddrCode(order.getAddress3());
                String address3 = null;
                try {
                    address3 = DictionaryController.instance().get("eh.base.dictionary.AddrArea").getText(order.getAddress3());
                } catch (ControllerException e) {
                    LOGGER.warn("杭州互联网虚拟药企-未获取收货地址名称-add={}", JSONUtils.toString(order.getAddress3()));

                }
                //收货地址名称
                updateTakeDrugWayReqTO.setReceiveAddress(address3);
                //期望配送日期
                updateTakeDrugWayReqTO.setConsignee(order.getExpectSendDate());
                //期望配送时间
                updateTakeDrugWayReqTO.setContactTel(order.getExpectSendTime());
            }else{
                LOGGER.info("杭州互联网虚拟药企-未获取有效订单-recipeId={}", JSONUtils.toString(recipe.getRecipeId()));
                result.setMsg("未获取有效订单");
                result.setCode(DrugEnterpriseResult.FAIL);
            }
        }

        HisResponseTO hisResult = service.updateTakeDrugWay(updateTakeDrugWayReqTO);
        if("200".equals(hisResult.getMsgCode())){
            LOGGER.info("杭州互联网虚拟药企-更新取药信息成功-his. param={},result={}", JSONUtils.toString(updateTakeDrugWayReqTO), JSONUtils.toString(hisResult));
            result.setCode(DrugEnterpriseResult.SUCCESS);
        }else{
            LOGGER.error("杭州互联网虚拟药企-更新取药信息失败-his. param={},result={}", JSONUtils.toString(updateTakeDrugWayReqTO), JSONUtils.toString(hisResult));

            result.setMsg(hisResult.getMsg());
            result.setCode(DrugEnterpriseResult.FAIL);
        }

        return result;
    }

    @Override
    public DrugEnterpriseResult scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
        LOGGER.info("旧-scanStock 虚拟药企库存入参为：{}，{}", recipeId, JSONUtils.toString(drugsEnterprise));
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise) {
        LOGGER.info("旧-findSupportDep 虚拟药企导出入参为：{}，{}，{}", JSONUtils.toString(recipeIds), JSONUtils.toString(ext), JSONUtils.toString(enterprise));
        return DrugEnterpriseResult.getSuccess();
    }

    @Override
    public boolean scanStock(Recipe dbRecipe, DrugsEnterprise dep, List<Integer> drugIds) {
        LOGGER.info("旧-scanStock recipeId:{},dep:{},drugIds:{}", dbRecipe.getRecipeId(), JSONUtils.toString(dep), JSONUtils.toString(drugIds));
        AccessDrugEnterpriseService remoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
        return remoteService.scanStock(dbRecipe, dep, drugIds);
    }

    @Override
    public String appEnterprise(RecipeOrder order) {
        LOGGER.info("旧-appEnterprise order:{}", JSONUtils.toString(order));
        AccessDrugEnterpriseService remoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
        return remoteService.appEnterprise(order);
    }

    @Override
    public BigDecimal orderToRecipeFee(RecipeOrder order, List<Integer> recipeIds, RecipePayModeSupportBean payModeSupport, BigDecimal recipeFee, Map<String, String> extInfo) {
        LOGGER.info("旧-orderToRecipeFee order:{}, recipeIds:{}, payModeSupport:{}, recipeFee:{}, extInfo:{}",
                JSONUtils.toString(order), JSONUtils.toString(recipeIds), JSONUtils.toString(payModeSupport), recipeFee, JSONUtils.toString(extInfo));
        AccessDrugEnterpriseService remoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
        return remoteService.orderToRecipeFee(order, recipeIds, payModeSupport, recipeFee, extInfo);
    }

    @Override
    public void setOrderEnterpriseMsg(Map<String, String> extInfo, RecipeOrder order) {

        LOGGER.info("旧-setOrderEnterpriseMsg extInfo：{}，order：{}", JSONUtils.toString(extInfo), JSONUtils.toString(order));
        AccessDrugEnterpriseService remoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
        remoteService.setOrderEnterpriseMsg(extInfo, order);
    }

    @Override
    public void checkRecipeGiveDeliveryMsg(RecipeBean recipeBean, Map<String, Object> map) {

        LOGGER.info("旧-checkRecipeGiveDeliveryMsg recipeBean:{}, map:{}", JSONUtils.toString(recipeBean), JSONUtils.toString(map));
        AccessDrugEnterpriseService remoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
        remoteService.checkRecipeGiveDeliveryMsg(recipeBean, map);
    }

    @Override
    public void setEnterpriseMsgToOrder(RecipeOrder order, Integer depId, Map<String, String> extInfo) {
        LOGGER.info("旧-setEnterpriseMsgToOrder order:{}, depId:{}，extInfo:{} ", JSONUtils.toString(order), depId, JSONUtils.toString(extInfo));
        AccessDrugEnterpriseService remoteService = AppContextHolder.getBean("commonRemoteService", CommonRemoteService.class);
        remoteService.setEnterpriseMsgToOrder(order, depId, extInfo);
    }
}
