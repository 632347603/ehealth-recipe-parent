package recipe.purchase;

import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.service.OrganService;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.entity.Recipedetail;
import com.ngari.recipe.recipeorder.model.OrderCreateResult;
import ctd.persistence.DAOFactory;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ApplicationUtils;
import recipe.bean.RecipePayModeSupportBean;
import recipe.constant.OrderStatusConstant;
import recipe.constant.RecipeBussConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeDetailDAO;
import recipe.dao.RecipeOrderDAO;
import recipe.service.RecipeHisService;
import recipe.service.RecipeOrderService;
import recipe.util.MapValueUtil;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author： 0184/yu_yun
 * @date： 2019/6/20
 * @description： 到院取药方式实现
 * @version： 1.0
 */
public class PayModeToHos implements IPurchaseService{
    /**
     * logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(PurchaseService.class);

    @Override
    public RecipeResultBean findSupportDepList(Recipe dbRecipe, Map<String, String> extInfo) {
        RecipeDetailDAO detailDAO = DAOFactory.getDAO(RecipeDetailDAO.class);
        OrganService organService = ApplicationUtils.getBasicService(OrganService.class);
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        Integer recipeId = dbRecipe.getRecipeId();
        List<Recipedetail> detailList = detailDAO.findByRecipeId(recipeId);
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        OrganDTO organDTO = organService.getByOrganId(recipe.getClinicOrgan());
        StringBuilder sb = new StringBuilder();
        PurchaseService purchaseService = ApplicationUtils.getRecipeService(PurchaseService.class);
        //todo---暂时写死上海六院---配送到家判断是否是自费患者
        //到院取药非卫宁付
        if (!purchaseService.getToHosPayConfig(dbRecipe.getClinicOrgan())){
            if (dbRecipe.getClinicOrgan() == 1000899 && !purchaseService.isMedicarePatient(1000899,dbRecipe.getMpiid())){
                resultBean.setCode(RecipeResultBean.FAIL);
                resultBean.setMsg("自费患者不支持到院取药，请选择其他取药方式");
                return resultBean;
            }
        }
        //判断是否是慢病医保患者------郑州人民医院
        if (purchaseService.isMedicareSlowDiseasePatient(recipeId)){
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("抱歉，由于您是慢病医保患者，请到人社平台、医院指定药房或者到医院进行医保支付。");
            return resultBean;
        }

        //点击到院取药再次判断库存--防止之前开方的时候有库存流转到此无库存
        RecipeHisService hisService = ApplicationUtils.getRecipeService(RecipeHisService.class);
        RecipeResultBean scanResult = hisService.scanDrugStockByRecipeId(recipeId);
        if (RecipeResultBean.FAIL.equals(scanResult.getCode())) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("抱歉，医院没有库存，无法到医院取药，请选择其他购药方式。");
            return resultBean;
        }

        if(CollectionUtils.isNotEmpty(detailList)){
            String pharmNo = detailList.get(0).getPharmNo();
            if(StringUtils.isNotEmpty(pharmNo)){
                sb.append("选择到院自取后需去医院取药窗口取药：["+ organDTO.getName() + pharmNo + "取药窗口]");
            }else {
                sb.append("选择到院自取后，需去医院取药窗口取药");
            }
        }
        resultBean.setMsg(sb.toString());
        return resultBean;
    }

    @Override
    public OrderCreateResult order(Recipe dbRecipe, Map<String, String> extInfo) {
        OrderCreateResult result = new OrderCreateResult(RecipeResultBean.SUCCESS);
        //定义处方订单
        RecipeOrder order = new RecipeOrder();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);

        Integer payMode = MapValueUtil.getInteger(extInfo, "payMode");
        RecipePayModeSupportBean payModeSupport = orderService.setPayModeSupport(order, payMode);

        order.setMpiId(dbRecipe.getMpiid());
        order.setOrganId(dbRecipe.getClinicOrgan());
        order.setOrderCode(orderService.getOrderCode(order.getMpiId()));
        //订单的状态统一到finishOrderPayWithoutPay中设置
        order.setStatus(OrderStatusConstant.READY_GET_DRUG);
        order.setRecipeIdList("["+dbRecipe.getRecipeId()+"]");
        List<Recipe> recipeList = Arrays.asList(dbRecipe);
        Integer calculateFee = MapValueUtil.getInteger(extInfo, "calculateFee");
        CommonOrder.createDefaultOrder(extInfo, result, order, payModeSupport, recipeList, calculateFee);
        //设置为有效订单
        order.setEffective(1);
        boolean saveFlag = orderService.saveOrderToDB(order, recipeList, payMode, result, recipeDAO, orderDAO);
        if(!saveFlag){
            result.setCode(RecipeResultBean.FAIL);
            result.setMsg("提交失败，请重新提交。");
            return result;
        }
        orderService.setCreateOrderResult(result, order, payModeSupport, 1);
        //更新处方信息
        //更新处方信息
        if(0d >= order.getActualPrice()){
            //如果不需要支付则不走支付,直接掉支付后的逻辑
            orderService.finishOrderPay(order.getOrderCode(), 1, MapValueUtil.getInteger(extInfo, "payMode"));
        }else{
            //需要支付则走支付前的逻辑
            orderService.finishOrderPayWithoutPay(order.getOrderCode(), payMode);
        }
        return result;
    }

    @Override
    public Integer getPayMode() {
        return RecipeBussConstant.PAYMODE_TO_HOS;
    }

    @Override
    public String getServiceName() {
        return "payModeToHosService";
    }

    @Override
    public String getTipsByStatusForPatient(Recipe recipe, RecipeOrder order) {
        Integer status = recipe.getStatus();
        Integer payFlag = recipe.getPayFlag();
        String orderCode = recipe.getOrderCode();
        String tips = "";
        switch (status) {
            case RecipeStatusConstant.CHECK_PASS:
                //date 20190930
                //先判断是否需要支付，再判断有没有支付
                if (StringUtils.isNotEmpty(orderCode)) {
                    //上海马陆医院线下转线上处方直接去支付文案特殊化处理
                    if (new Integer(1).equals(recipe.getRecipePayType())) {
                        tips = "处方已支付，具体配送情况请咨询您的开方医生。";
                    } else {
                        if(0d >= order.getActualPrice()){
                            tips = "订单已处理，请到院取药";
                        }else if(0d < order.getActualPrice() && payFlag == 1){
                            tips = "订单已处理，请到院取药";
                        }
                    }
                }
                break;
            case RecipeStatusConstant.CHECK_PASS_YS:
                tips = "处方已审核通过，请到院取药";
                break;
            case RecipeStatusConstant.NO_DRUG:
            case RecipeStatusConstant.RECIPE_FAIL:
                tips = "到院取药失败";
                break;
            case RecipeStatusConstant.FINISH:
                tips = "到院取药成功，订单完成";
                break;
                default:
        }
        return tips;
    }

    @Override
    public Integer getOrderStatus(Recipe recipe) {
        return OrderStatusConstant.READY_GET_DRUG;
    }
}
