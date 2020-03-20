package recipe.drugsenterprise.compatible;

import com.alibaba.fastjson.JSON;
import com.alijk.bqhospital.alijk.conf.TaobaoConf;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.DeliveryList;
import com.ngari.recipe.drugsenterprise.model.DepDetailBean;
import com.ngari.recipe.entity.DrugsEnterprise;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.recipe.model.RecipeBean;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
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
import recipe.dao.OrganAndDrugsepRelationDAO;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeExtendDAO;
import recipe.purchase.PayModeOnline;
import recipe.purchase.PurchaseService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @description 杭州互联网（金投）对接服务新实现方式
 * @author JRK
 * @date 2020/3/16
 */
@Service("hzInternetRemoteNewType")
public class HzInternetRemoteNewType implements HzInternetRemoteTypeInterface {

    private static final Logger LOGGER = LoggerFactory.getLogger(HzInternetRemoteNewType.class);

    private static final String EXPIRE_TIP = "请重新授权";

    @Autowired
    private RecipeExtendDAO recipeExtendDAO;

    @Autowired
    private TaobaoConf taobaoConf;

    @Override
    public DrugEnterpriseResult pushRecipeInfo(List<Integer> recipeIds, DrugsEnterprise enterprise) {
        LOGGER.info("新-pushRecipeInfo杭州互联网虚拟药企-更新取药信息至处方流转平台开始，处方ID：{}.", JSONUtils.toString(recipeIds));

        //虚拟药企推送，修改配送信息的逻辑调整到前面确认订单
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        return result;
    }

    @Override
    public DrugEnterpriseResult scanStock(Integer recipeId, DrugsEnterprise drugsEnterprise) {
        //date 20200311
        //查询库存通过his预校验的返回判断库存是否足够
        LOGGER.info("新-scanStock 虚拟药企库存入参为：{}，{}", recipeId, JSONUtils.toString(drugsEnterprise));
        DrugEnterpriseResult result = DrugEnterpriseResult.getFail();
        if(!valiScanStock(recipeId, drugsEnterprise, result)){
            return result;
        }
        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeExtend extend = recipeExtendDAO.getByRecipeId(recipeId);
        if(null != extend){
            //获取当前his返回的药企信息，以及价格信息
            String deliveryRecipeFees = extend.getDeliveryRecipeFee();
            String deliveryCodes = extend.getDeliveryCode();
            String deliveryNames = extend.getDeliveryName();
            if(StringUtils.isNotEmpty(deliveryRecipeFees) &&
                    StringUtils.isNotEmpty(deliveryCodes) && StringUtils.isNotEmpty(deliveryNames)){
                //只有杭州是互联网医院返回的是库存足够
                result.setCode(DrugEnterpriseResult.SUCCESS);
                result.setMsg("调用[" + drugsEnterprise.getName() + "][ scanStock ]结果返回成功,有库存,处方单ID:"+recipeId+".");
                return result;
            }
        }
        return result;
    }

    private boolean valiScanStock(Integer recipeId, DrugsEnterprise drugsEnterprise, DrugEnterpriseResult result) {
        if(null == recipeId){
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setError("传入的处方id为空！");
            return false;
        }
        return true;
    }

    @Override
    public DrugEnterpriseResult findSupportDep(List<Integer> recipeIds, Map ext, DrugsEnterprise enterprise) {
        LOGGER.info("新-findSupportDep 虚拟药企导出入参为：{}，{}，{}", JSONUtils.toString(recipeIds), JSONUtils.toString(ext), JSONUtils.toString(enterprise));
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        //校验入参
        if(!valiRequestDate(recipeIds, ext, result)){
            return result;
        }
        //date 20200311
        //修改逻辑：将his返回的药企列表信息回传成
        Integer recipeId = recipeIds.get(0);

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);

        OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO = DAOFactory.getDAO(OrganAndDrugsepRelationDAO.class);
        Recipe recipe = recipeDAO.getByRecipeId(recipeIds.get(0));
        if(null == recipe){
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setError("当前处方" + recipeIds.get(0) + "不存在！");
            return result;
        }
        List<DrugsEnterprise> drugsEnterprises = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(recipe.getClinicOrgan(), 1);
        DrugsEnterprise drugsEnterprise;
        if(CollectionUtils.isNotEmpty(drugsEnterprises)){
            //这里杭州市互联网医院只配置一个虚拟药企
            drugsEnterprise = drugsEnterprises.get(0);
        }else{
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setError("当前医院"+ recipe.getClinicOrgan() +"没有设置关联药企！");
            return result;
        }

        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeExtend extend = recipeExtendDAO.getByRecipeId(recipeId);
        List<DepDetailBean> depDetailList = new ArrayList<>();
        if(null != extend){
            //获取当前his返回的药企信息，以及价格信息
            String deliveryRecipeFees = extend.getDeliveryRecipeFee();
            String deliveryCodes = extend.getDeliveryCode();
            String deliveryNames = extend.getDeliveryName();
            DepDetailBean depDetailBean;
            if(StringUtils.isNotEmpty(deliveryRecipeFees) &&
                    StringUtils.isNotEmpty(deliveryCodes) && StringUtils.isNotEmpty(deliveryNames)){
                LOGGER.info("新-findSupportDepList 当前处方{}的药企信息为his预校验返回信息：{}", recipeId, JSONUtils.toString(extend));
                String[] deliveryRecipeFeeList = deliveryRecipeFees.split("\\|");
                String[] deliveryCodeList = deliveryCodes.split("\\|");
                String[] deliveryNameList = deliveryNames.split("\\|");

                for(int i = 1; i < deliveryRecipeFeeList.length ; i++){
                    depDetailBean = new DepDetailBean();
                    //标识选择的药企是his推过来的
                    depDetailBean.setDepId(drugsEnterprise.getId());
                    depDetailBean.setDepName(deliveryNameList[i]);
                    depDetailBean.setRecipeFee(new BigDecimal(deliveryRecipeFeeList[i]));
                    depDetailBean.setBelongDepName(deliveryNameList[i]);
                    depDetailBean.setOrderType(1);
                    depDetailBean.setPayModeText("在线支付");
                    depDetailBean.setPayMode(RecipeBussConstant.PAYMODE_ONLINE);
                    //预留字段标识是医院推送给过来的
                    depDetailBean.setHisDep(true);
                    depDetailBean.setHisDepCode(deliveryCodeList[i]);
                    //date 20200311
                    //医院返回的药企处方金额
                    depDetailBean.setHisDepFee(new BigDecimal(deliveryRecipeFeeList[i]));

                    depDetailList.add(depDetailBean);
                }



            }

        }
        LOGGER.info("新-findSupportDepList 虚拟药企处方{}查询his药企列表展示信息：{}", recipeId, JSONUtils.toString(depDetailList));
        result.setObject(depDetailList);
        return result;
    }

    private Boolean valiRequestDate(List<Integer> recipeIds, Map ext, DrugEnterpriseResult result) {
        if (CollectionUtils.isEmpty(recipeIds)) {
            result.setCode(DrugEnterpriseResult.FAIL);
            result.setError("传入的处方id为空！");
            return false;
        }

        return true;
    }

    @Override
    public boolean scanStock(Recipe dbRecipe, DrugsEnterprise dep, List<Integer> drugIds) {
        LOGGER.info("新-scanStock recipeId:{},dep:{},drugIds:{}", dbRecipe.getRecipeId(), JSONUtils.toString(dep), JSONUtils.toString(drugIds));
        DrugEnterpriseResult result = scanStock(dbRecipe.getRecipeId(), dep);
        LOGGER.info("新-scanStock recipeId:{}, result:{}", dbRecipe.getRecipeId(), JSONUtils.toString(result));
        boolean equals = result.getCode().equals(DrugEnterpriseResult.SUCCESS);
        LOGGER.info("新-scanStock 请求虚拟药企返回：{}", equals);
        return equals;
    }

    @Override
    public String appEnterprise(RecipeOrder order) {
        LOGGER.info("新-appEnterprise order:{}", JSONUtils.toString(order));
        String hisEnterpriseName = null;
        if (null != order) {

            hisEnterpriseName = order.getHisEnterpriseName();
        }
        LOGGER.info("新-appEnterprise 请求虚拟药企返回：{}", hisEnterpriseName);
        return hisEnterpriseName;
    }

    @Override
    public BigDecimal orderToRecipeFee(RecipeOrder order, List<Integer> recipeIds, RecipePayModeSupportBean payModeSupport, BigDecimal recipeFee, Map<String, String> extInfo) {
        LOGGER.info("新-orderToRecipeFee order:{}, recipeIds:{}, payModeSupport:{}, recipeFee:{}, extInfo:{}",
                JSONUtils.toString(order), JSONUtils.toString(recipeIds), JSONUtils.toString(payModeSupport), recipeFee, JSONUtils.toString(extInfo));
        BigDecimal depFee = recipeFee;
        String hisDepFee = extInfo.get("hisDepFee");
        if(StringUtils.isNotEmpty(hisDepFee)){
            depFee = new BigDecimal(hisDepFee);
        }
        LOGGER.info("新-orderToRecipeFee 请求虚拟药企返回：{}", depFee);
        return depFee;
    }

    @Override
    public void setOrderEnterpriseMsg(Map<String, String> extInfo, RecipeOrder order) {
        LOGGER.info("新-setOrderEnterpriseMsg extInfo：{}，order：{}", JSONUtils.toString(extInfo), JSONUtils.toString(order));
        if(null != extInfo){
            //date 20200312
            //设置患者选中his回传信息
            order.setHisEnterpriseName(extInfo.get("depName"));
            order.setHisEnterpriseCode(extInfo.get("hisDepCode"));
        }
        LOGGER.info("新-setOrderEnterpriseMsg 当前虚拟药企组装的订单：{}", JSONUtils.toString(order));
    }

//    @Override
//    public void checkRecipeGiveDeliveryMsg(RecipeBean recipeBean, Map<String, Object> map) {
//        LOGGER.info("新-checkRecipeGiveDeliveryMsg recipeBean:{}, map:{}", JSONUtils.toString(recipeBean), JSONUtils.toString(map));
//        String giveMode = null != map.get("giveMode") ? map.get("giveMode").toString() : null;
//        Object deliveryList = map.get("deliveryList");
//        if(null != deliveryList && null != giveMode){
//
//            List<Map> deliveryLists = (List<Map>)deliveryList;
//            //暂时按照逻辑只保存展示返回的第一个药企
//            DeliveryList nowDeliveryList = JSON.parseObject(JSON.toJSONString(deliveryLists.get(0)), DeliveryList.class);
//            RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
//            if (null != nowDeliveryList){
//                Map<String,String> updateMap = Maps.newHashMap();
//                updateMap.put("deliveryCode", nowDeliveryList.getDeliveryCode());
//                updateMap.put("deliveryName", nowDeliveryList.getDeliveryName());
//                //存放处方金额
//                updateMap.put("deliveryRecipeFee", null != nowDeliveryList.getRecipeFee() ? nowDeliveryList.getRecipeFee().toString() : null);
//                recipeExtendDAO.updateRecipeExInfoByRecipeId(recipeBean.getRecipeId(), updateMap);
//            }
//            //date 20200311
//            //将his返回的批量药企信息存储下来，将信息分成|分割
//            DeliveryList deliveryListNow;
//            Map<String,String> updateMap = Maps.newHashMap();
//            StringBuffer deliveryCodes = new StringBuffer().append("|");
//            StringBuffer deliveryNames = new StringBuffer().append("|");
//            StringBuffer deliveryRecipeFees = new StringBuffer().append("|");
//            for(Map<String,String> delivery : deliveryLists){
//                deliveryListNow = JSON.parseObject(JSON.toJSONString(delivery), DeliveryList.class);
//                deliveryCodes.append(deliveryListNow.getDeliveryCode()).append("|");
//                deliveryNames.append(deliveryListNow.getDeliveryName()).append("|");
//                deliveryRecipeFees.append(deliveryListNow.getRecipeFee()).append("|");
//            }
//            updateMap.put("deliveryCode", "|".equals(deliveryCodes) ? null : deliveryCodes.toString());
//            updateMap.put("deliveryName", "|".equals(deliveryNames) ? null : deliveryNames.toString());
//            //存放处方金额
//            updateMap.put("deliveryRecipeFee", "|".equals(deliveryRecipeFees) ? null : deliveryRecipeFees.toString());
//            recipeExtendDAO.updateRecipeExInfoByRecipeId(recipeBean.getRecipeId(), updateMap);
//            LOGGER.info("hisRecipeCheck 当前处方{}预校验，配送方式存储成功:{}！", recipeBean.getRecipeId(), JSONUtils.toString(updateMap));
//
//        }else{
//            LOGGER.info("hisRecipeCheck 当前处方{}预校验，配送方式没有返回药企信息！", recipeBean.getRecipeId());
//        }
//    }

    @Override
    public void setEnterpriseMsgToOrder(RecipeOrder order, Integer depId, Map<String, String> extInfo) {
        LOGGER.info("新-setEnterpriseMsgToOrder order:{}, depId:{}，extInfo:{} ", JSONUtils.toString(order), depId, JSONUtils.toString(extInfo));
        order.setEnterpriseId(depId);
        if(null != extInfo){
            //date 20200312
            //设置患者选中his回传信息
            order.setHisEnterpriseName(extInfo.get("depName"));
            order.setHisEnterpriseCode(extInfo.get("hisDepCode"));
        }
        LOGGER.info("新-setEnterpriseMsgToOrder 当前虚拟药企组装的订单：{}", JSONUtils.toString(order));
    }

    @Override
    public Boolean specialMakeDepList(DrugsEnterprise drugsEnterprise, Recipe dbRecipe) {
        LOGGER.info("新-杭州互联网虚拟药企个性化展示药企列表 specialMakeDepList drugsEnterprise:{}，dbRecipe:{} "
                , JSONUtils.toString(drugsEnterprise), JSONUtils.toString(dbRecipe));
        return true;
    }

    @Override
    public void sendDeliveryMsgToHis(Integer recipeId) {
        LOGGER.info("新-杭州互联网虚拟药企确认订单后同步配送信息 sendDeliveryMsgToHis recipeId:{}", recipeId);
        return;
    }

    @Override
    public DrugEnterpriseResult sendMsgResultMap(Integer recipeId, Map<String, String> extInfo, DrugEnterpriseResult payResult) {
        LOGGER.info("新-sendMsgResultMap杭州互联网虚拟药企确认订单前检验订单信息同步配送信息，入参：dbRecipe:{},extInfo:{},payResult:{]",
                recipeId, JSONUtils.toString(extInfo), JSONUtils.toString(payResult));
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        PurchaseService purchaseService = ApplicationUtils.getRecipeService(PurchaseService.class);
        PayModeOnline service = (PayModeOnline)purchaseService.getService(1);
        HisResponseTO resultSave = service.updateGoodsReceivingInfoToCreateOrder(recipeId, extInfo);

        if(null != resultSave) {
            if(resultSave.isSuccess() && null != resultSave.getData()){

                Map<String, Object> data = (Map<String, Object>) resultSave.getData();

                if (null != data.get("recipeCode")) {
                    //新增成功更新his处方code
                    recipeDAO.updateRecipeInfoByRecipeId(recipeId,
                            ImmutableMap.of("recipeCode", data.get("recipeCode").toString()));
                    LOGGER.info("order 当前处方{}确认订单流程：his新增成功",
                            recipeId);
                    return payResult;
                } else {
                    payResult.setCode(DrugEnterpriseResult.FAIL);
                    payResult.setMsg("订单信息校验失败");
                    LOGGER.info("order 当前处方确认订单的his同步配送信息，没有返回his处方code：{}", JSONUtils.toString(resultSave));
                    return payResult;
                }
            }else{
                payResult.setCode(DrugEnterpriseResult.FAIL);
                payResult.setMsg("订单信息校验失败");
                LOGGER.info("order 当前处方确认订单的his同步配送信息失败，返回：{}", JSONUtils.toString(resultSave));
                return payResult;
            }
        }else {
            LOGGER.info("order 当前处方{}没有对接同步配送信息，默认成功！", recipeId);
            return payResult;
        }
    }
}
