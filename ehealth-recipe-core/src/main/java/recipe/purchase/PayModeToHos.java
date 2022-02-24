package recipe.purchase;

import com.google.common.collect.ImmutableMap;
import com.ngari.his.recipe.mode.TakeMedicineByToHos;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.service.OrganService;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.drugsenterprise.model.DepDetailBean;
import com.ngari.recipe.drugsenterprise.model.DepListBean;
import com.ngari.recipe.drugsenterprise.model.Position;
import com.ngari.recipe.dto.EnterpriseStock;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipeorder.model.OrderCreateResult;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.ApplicationUtils;
import recipe.bean.RecipePayModeSupportBean;
import recipe.client.IConfigurationClient;
import recipe.constant.OrderStatusConstant;
import recipe.constant.RecipeBussConstant;
import recipe.core.api.IStockBusinessService;
import recipe.dao.*;
import recipe.drugsenterprise.AccessDrugEnterpriseService;
import recipe.drugsenterprise.RemoteDrugEnterpriseService;
import recipe.enumerate.status.GiveModeEnum;
import recipe.enumerate.status.PayModeEnum;
import recipe.enumerate.status.RecipeStatusEnum;
import recipe.enumerate.type.RecipeSupportGiveModeEnum;
import recipe.manager.EnterpriseManager;
import recipe.manager.OrderManager;
import recipe.manager.OrganDrugListManager;
import recipe.service.RecipeOrderService;
import recipe.util.MapValueUtil;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static ctd.persistence.DAOFactory.getDAO;

/**
 * @author： 0184/yu_yun
 * @date： 2019/6/20
 * @description： 到院取药方式实现
 * @version： 1.0
 */
public class PayModeToHos implements IPurchaseService {

    @Autowired
    private OrderManager orderManager;
    @Autowired
    private OrganDrugListManager organDrugListManager;
    @Autowired
    private RecipeDetailDAO recipeDetailDAO;
    @Autowired
    private IConfigurationClient configurationClient;
    @Autowired
    private OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO;
    @Autowired
    private OrganDrugsSaleConfigDAO organDrugsSaleConfigDAO;
    @Autowired
    private EnterpriseManager enterpriseManager;
    @Autowired
    private PurchaseService purchaseService;
    @Autowired
    private DrugsEnterpriseDAO drugsEnterpriseDAO;
    @Autowired
    private IStockBusinessService stockBusinessService;
    /**
     * logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(PurchaseService.class);

    @Override
    public RecipeResultBean findSupportDepList(Recipe dbRecipe, Map<String, String> extInfo) {
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        //判断是否是慢病医保患者------郑州人民医院
        if (purchaseService.isMedicareSlowDiseasePatient(dbRecipe.getRecipeId())) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("抱歉，由于您是慢病医保患者，请到人社平台、医院指定药房或者到医院进行医保支付。");
            return resultBean;
        }

        // 到院自取是否采用药企管理模式
        Boolean drugToHosByEnterprise = configurationClient.getValueBooleanCatch(dbRecipe.getClinicOrgan(), "drugToHosByEnterprise", false);
        if (drugToHosByEnterprise) {
            resultBean = newModeFindSupportDepList(dbRecipe, extInfo);
        } else {
            resultBean = oldModeFindSupportDepList(dbRecipe);
        }
        return resultBean;
    }


    @Override
    public OrderCreateResult order(List<Recipe> dbRecipes, Map<String, String> extInfo) {
        OrderCreateResult result = new OrderCreateResult(RecipeResultBean.SUCCESS);
        //定义处方订单
        RecipeOrder order = new RecipeOrder();
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        RecipeOrderService orderService = ApplicationUtils.getRecipeService(RecipeOrderService.class);

        Integer payMode = MapValueUtil.getInteger(extInfo, "payMode");
        RecipePayModeSupportBean payModeSupport = orderService.setPayModeSupport(order, payMode);
        List<Integer> recipeIdLists = dbRecipes.stream().map(Recipe::getRecipeId).collect(Collectors.toList());
        // 到院自取是否采用药企管理模式
        Boolean drugToHosByEnterprise = configurationClient.getValueBooleanCatch(dbRecipes.get(0).getClinicOrgan(), "drugToHosByEnterprise", false);
        if (drugToHosByEnterprise) {
            Integer depId = MapValueUtil.getInteger(extInfo, "depId");
            DrugsEnterprise dep = drugsEnterpriseDAO.get(depId);
            //处理详情
            for (Recipe dbRecipe : dbRecipes) {
                List<Recipedetail> detailList = recipeDetailDAO.findByRecipeId(dbRecipe.getRecipeId());

                order.setRecipeIdList(JSONUtils.toString(recipeIdLists));
                RemoteDrugEnterpriseService remoteDrugEnterpriseService =
                        ApplicationUtils.getRecipeService(RemoteDrugEnterpriseService.class);

                AccessDrugEnterpriseService remoteService = remoteDrugEnterpriseService.getServiceByDep(dep);

                // 根据药企查询库存
                EnterpriseStock enterpriseStock = stockBusinessService.enterpriseStockCheck(dbRecipe, detailList, depId);
                if (Objects.isNull(enterpriseStock) || !enterpriseStock.getStock()) {
                    //无法配送
                    result.setCode(RecipeResultBean.FAIL);
                    result.setMsg("药企无法配送");
                    return result;
                } else {
                    remoteService.setEnterpriseMsgToOrder(order, depId, extInfo);
                }
            }
        } else {
            // 到院取药校验机构库存
            List<Recipedetail> recipeDetails = recipeDetailDAO.findByRecipeIdList(recipeIdLists);
            EnterpriseStock organStock = organDrugListManager.organStock(dbRecipes.get(0).getClinicOrgan(), recipeDetails);
            if (Objects.isNull(organStock) || !organStock.getStock()) {
                result.setCode(RecipeResultBean.FAIL);
                result.setMsg("抱歉，医院没有库存，无法到医院取药，请选择其他购药方式。");
                return result;
            }
        }
        order.setMpiId(dbRecipes.get(0).getMpiid());
        order.setOrganId(dbRecipes.get(0).getClinicOrgan());
        order.setOrderCode(orderService.getOrderCode(order.getMpiId()));
        order.setExpectSendDate(MapValueUtil.getString(extInfo, "expectSendDate"));
        order.setExpectSendTime(MapValueUtil.getString(extInfo, "expectSendTime"));
        //设置预约取药开始和结束时间
        order.setExpectEndTakeTime(MapValueUtil.getString(extInfo, "expectEndTakeTime"));
        order.setExpectStartTakeTime(MapValueUtil.getString(extInfo, "expectStartTakeTime"));
        //订单的状态统一到finishOrderPayWithoutPay中设置
        order.setStatus(OrderStatusConstant.READY_GET_DRUG);
        order.setRecipeIdList(JSONUtils.toString(recipeIdLists));

        Integer calculateFee = MapValueUtil.getInteger(extInfo, "calculateFee");
        //设置中药代建费
        Integer decoctionId = MapValueUtil.getInteger(extInfo, "decoctionId");
        if (decoctionId != null) {
            DrugDecoctionWayDao drugDecoctionWayDao = getDAO(DrugDecoctionWayDao.class);
            DecoctionWay decoctionWay = drugDecoctionWayDao.get(decoctionId);
            RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
            for (Recipe dbRecipe : dbRecipes) {
                if (decoctionWay != null) {
                    if (decoctionWay.getDecoctionPrice() != null) {
                        calculateFee = 1;
                        order.setDecoctionUnitPrice(BigDecimal.valueOf(decoctionWay.getDecoctionPrice()));
                    }
                    recipeExtendDAO.updateRecipeExInfoByRecipeId(dbRecipe.getRecipeId(), ImmutableMap.of("decoctionId", decoctionId + "", "decoctionText", decoctionWay.getDecoctionText()));
                } else {
                    LOG.error("未获取到对应的代煎费，recipeId={},decoctionId={}", dbRecipe.getRecipeId(), decoctionId);
                }
            }
        }
        CommonOrder.createDefaultOrder(extInfo, result, order, payModeSupport, dbRecipes, calculateFee);
        //设置为有效订单
        order.setEffective(1);
        // 目前paymode传入还是老版本 除线上支付外全都算线下支付,下个版本与前端配合修改
        Integer payModeNew = payMode;
        // 到院取药是否支持线上支付
        OrganDrugsSaleConfig organDrugsSaleConfig = enterpriseManager.getOrganDrugsSaleConfig(order.getOrganId(), order.getEnterpriseId(), GiveModeEnum.GIVE_MODE_HOSPITAL_DRUG.getType());
        Integer takeOneselfPayment = organDrugsSaleConfig.getTakeOneselfPayment();
        if (!payMode.equals(1)) {
            payModeNew = 2;
        }
        if (new Integer(1).equals(takeOneselfPayment)) {
            payModeNew = 1;
        }
        order.setPayMode(payModeNew);
        boolean saveFlag = orderService.saveOrderToDB(order, dbRecipes, payMode, result, recipeDAO, orderDAO);
        if (!saveFlag) {
            result.setCode(RecipeResultBean.FAIL);
            result.setMsg("提交失败，请重新提交。");
            return result;
        }
        orderService.setCreateOrderResult(result, order, payModeSupport, 1);
        //更新处方信息
        //更新处方信息
        if (0d >= order.getActualPrice()) {
            //如果不需要支付则不走支付,直接掉支付后的逻辑
            orderService.finishOrderPay(order.getOrderCode(), 1, MapValueUtil.getInteger(extInfo, "payMode"));
        } else {
            // 邵逸夫模式下 不需要审方物流费需要生成一条流水记录
            orderManager.saveFlowByOrder(order);

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
        switch (RecipeStatusEnum.getRecipeStatusEnum(status)) {
            case RECIPE_STATUS_CHECK_PASS:
                //date 20190930
                //先判断是否需要支付，再判断有没有支付
                if (StringUtils.isNotEmpty(orderCode)) {
                    //上海马陆医院线下转线上处方直接去支付文案特殊化处理
                    if (new Integer(1).equals(recipe.getRecipePayType()) && order.getPayFlag() == 1) {
                        tips = "处方已支付，具体配送情况请咨询您的开方医生。";
                    } else {
                        if (0d >= order.getActualPrice()) {
                            tips = "订单已处理，请到院取药";
                        } else if (0d < order.getActualPrice() && payFlag == 1) {
                            tips = "订单已处理，请到院取药";
                        }
                    }
                }
                break;
            case RECIPE_STATUS_CHECK_PASS_YS:
                tips = "处方已审核通过，请到院取药";
                break;
            case RECIPE_STATUS_RECIPE_FAIL:
                tips = "到院取药失败";
                break;
            case RECIPE_STATUS_FINISH:
                tips = "到院取药成功，订单完成";
                break;
            case RECIPE_STATUS_HAVE_PAY:
                tips = "订单已支付，请到院取药";
            default:
        }
        return tips;
    }

    @Override
    public Integer getOrderStatus(Recipe recipe) {
        return OrderStatusConstant.READY_GET_DRUG;
    }

    @Override
    public void setRecipePayWay(RecipeOrder recipeOrder) {
        RecipeOrderDAO recipeOrderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        recipeOrder.setPayMode(2);
        recipeOrderDAO.update(recipeOrder);
    }

    /**
     * 新模式获取到院取药支持的药企
     *
     * @param dbRecipe
     * @return
     */
    private RecipeResultBean newModeFindSupportDepList(Recipe dbRecipe, Map<String, String> extInfo) {
        Integer recipeId = dbRecipe.getRecipeId();
        RecipeResultBean resultBean = RecipeResultBean.getSuccess();
        DepListBean depListBean = new DepListBean();
        String sort = extInfo.get("sort");
        // 库存判断
        List<OrganAndDrugsepRelation> relation = organAndDrugsepRelationDAO.getRelationByOrganIdAndGiveMode(dbRecipe.getClinicOrgan(), RecipeSupportGiveModeEnum.SUPPORT_TO_HOS.getType());
        if (CollectionUtils.isEmpty(relation)) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("抱歉，该机构无支持到院取药的药企，无法到医院取药，请选择其他购药方式。");
            return resultBean;
        }
        List<Integer> depIds = relation.stream().map(OrganAndDrugsepRelation::getDrugsEnterpriseId).collect(Collectors.toList());
        List<DrugsEnterprise> drugsEnterprises = drugsEnterpriseDAO.findByIds(depIds);
        if (CollectionUtils.isEmpty(drugsEnterprises)) {
            LOG.warn("findSupportDepList 处方[{}]没有任何药企可以进行到院取药！", recipeId);
            resultBean.setCode(5);
            resultBean.setMsg("抱歉，未获取到相关药房信息，您可以选择其他方式购药");
            return resultBean;
        }


        // 调his获取取药点
        List<TakeMedicineByToHos> takeMedicineByToHosList = enterpriseManager.getTakeMedicineByToHosList(dbRecipe.getClinicOrgan(), dbRecipe);

        if (CollectionUtils.isEmpty(takeMedicineByToHosList)) {
            LOG.warn("findSupportDepList 该处方无法配送. recipeId=[{}]", recipeId);
            resultBean.setCode(5);
            resultBean.setMsg("抱歉，未获取到相关药房信息，您可以选择其他方式购药");
            return resultBean;
        }

        LOG.info("findSupportDepList subDepList:{}", JSONUtils.toString(takeMedicineByToHosList));
        depListBean.setSigle(false);
        if (CollectionUtils.isNotEmpty(takeMedicineByToHosList) && takeMedicineByToHosList.size() == 1) {
            depListBean.setSigle(true);
        }
        List<Integer> saleDepIds = takeMedicineByToHosList.stream().map(TakeMedicineByToHos::getEnterpriseId).collect(Collectors.toList());
        List<OrganDrugsSaleConfig> organDrugsSaleConfigs = organDrugsSaleConfigDAO.findSaleConfigs(saleDepIds);
        Map<Integer, List<OrganDrugsSaleConfig>> saleMap = null;
        if (CollectionUtils.isNotEmpty(organDrugsSaleConfigs)) {
            saleMap = organDrugsSaleConfigs.stream().collect(Collectors.groupingBy(OrganDrugsSaleConfig::getDrugsEnterpriseId));
        }
        depListBean.setList(getDepDetailList(takeMedicineByToHosList,saleMap));
        List<DepDetailBean> result = getDepDetailList(takeMedicineByToHosList,saleMap);
        if ("1".equals(sort)) {
            //价格优先
            result = result.stream().sorted(Comparator.comparing(DepDetailBean::getRecipeFee)).collect(Collectors.toList());
        } else {
            //距离优先
            result = result.stream().sorted(Comparator.comparing(DepDetailBean::getDistance)).collect(Collectors.toList());
        }
        depListBean.setList(result);
        resultBean.setObject(depListBean);
        LOG.info("findSupportDepList 当前处方{}查询药企列表信息：{}", recipeId, JSONUtils.toString(resultBean));
        return resultBean;
    }

    private List<DepDetailBean> getDepDetailList(List<TakeMedicineByToHos> takeMedicineByToHosList,Map<Integer, List<OrganDrugsSaleConfig>> saleMap) {
        return takeMedicineByToHosList.stream().map(takeMedicineByToHos -> {
            DepDetailBean depDetailBean = new DepDetailBean();
            depDetailBean.setDepId(takeMedicineByToHos.getEnterpriseId());
            depDetailBean.setDepName(takeMedicineByToHos.getPharmacyName());
            depDetailBean.setBelongDepName(takeMedicineByToHos.getEnterpriseName());
            depDetailBean.setPharmacyName(takeMedicineByToHos.getPharmacyName());
            depDetailBean.setPharmacyCode(takeMedicineByToHos.getPharmacyCode());
            depDetailBean.setAddress(takeMedicineByToHos.getPharmacyAddress());
            depDetailBean.setDistance(takeMedicineByToHos.getDistance());
            depDetailBean.setRecipeFee(takeMedicineByToHos.getRecipeTotalPrice());
            depDetailBean.setPayMethod(takeMedicineByToHos.getPayWay().toString());
            if(MapUtils.isNotEmpty(saleMap)) {
                depDetailBean.setPayModeText(PayModeEnum.getPayModeEnumName(saleMap.get(takeMedicineByToHos.getEnterpriseId()).get(0).getTakeOneselfPayment()));
            }
            Position position = new Position();
            position.setLatitude(Double.valueOf(takeMedicineByToHos.getLat()));
            position.setLongitude(Double.valueOf(takeMedicineByToHos.getLng()));
            position.setRange(takeMedicineByToHos.getRange());
            depDetailBean.setPosition(position);
            return depDetailBean;
        }).collect(Collectors.toList());
    }

    /**
     * 老模式到院取药获取药企列表
     *
     * @param dbRecipe
     * @return
     */
    private RecipeResultBean oldModeFindSupportDepList(Recipe dbRecipe) {
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
        Integer takeOneselfPaymentChannel = configurationClient.getValueCatch(dbRecipe.getClinicOrgan(), "payModeToHosOnlinePayConfig", 1);

        if (!new Integer(2).equals(takeOneselfPaymentChannel)) {
            if (dbRecipe.getClinicOrgan() == 1000899 && !purchaseService.isMedicarePatient(1000899, dbRecipe.getMpiid())) {
                resultBean.setCode(RecipeResultBean.FAIL);
                resultBean.setMsg("自费患者不支持到院取药，请选择其他取药方式");
                return resultBean;
            }
        }

        //点击到院取药再次判断库存--防止之前开方的时候有库存流转到此无库存
        // 到院取药校验机构库存
        EnterpriseStock organStock = organDrugListManager.organStock(recipe.getClinicOrgan(), detailList);
        if (Objects.isNull(organStock) || !organStock.getStock()) {
            resultBean.setCode(RecipeResultBean.FAIL);
            resultBean.setMsg("抱歉，医院没有库存，无法到医院取药，请选择其他购药方式。");
            return resultBean;
        }

        RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);
        RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipeId);
        if (!Objects.isNull(recipeExtend) && StringUtils.isNotEmpty(recipeExtend.getPharmNo())) {
            String pharmNo = recipeExtend.getPharmNo();
            if (StringUtils.isNotEmpty(pharmNo)) {
                sb.append("选择到院自取后需去医院取药窗口取药：[" + organDTO.getName() + pharmNo + "取药窗口]");
            } else {
                sb.append("选择到院自取后，需去医院取药窗口取药");
            }
        }
        resultBean.setMsg(sb.toString());
        return resultBean;

    }
}
