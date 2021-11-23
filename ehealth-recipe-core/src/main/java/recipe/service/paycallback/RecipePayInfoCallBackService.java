package recipe.service.paycallback;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.ngari.recipe.RecipeAPI;
import com.ngari.recipe.common.RecipeOrderBillReqTO;
import com.ngari.recipe.common.RecipeResultBean;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.entity.RecipeOrderPayFlow;
import com.ngari.recipe.pay.model.PayResultDTO;
import com.ngari.recipe.pay.service.IRecipePayCallBackService;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.service.IRecipeService;
import com.ngari.recipe.recipeorder.model.RecipeOrderBean;
import coupon.api.service.ICouponBaseService;
import ctd.util.AppContextHolder;
import ctd.util.Base64;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import ctd.util.converter.ConversionUtils;
import eh.cdr.constant.RecipeConstant;
import eh.cdr.constant.RecipeStatusConstant;
import eh.entity.bus.Order;
import eh.utils.ValidateUtil;
import eh.wxpay.constant.PayConstant;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.client.IConfigurationClient;
import recipe.constant.CacheConstant;
import recipe.enumerate.type.ExpressFeePayWayEnum;
import recipe.enumerate.type.PayBusTypeEnum;
import recipe.enumerate.type.PayFlagEnum;
import recipe.enumerate.type.PayFlowTypeEnum;
import recipe.manager.OrderManager;
import recipe.manager.RecipeOrderPayFlowManager;
import recipe.service.PayModeGiveModeUtil;
import recipe.serviceprovider.recipelog.service.RemoteRecipeLogService;
import recipe.serviceprovider.recipeorder.service.RemoteRecipeOrderService;
import recipe.util.RedisClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * created by shiyuping on 2021/1/25
 */
@RpcBean
public class RecipePayInfoCallBackService implements IRecipePayCallBackService {
    private static final Logger logger = LoggerFactory.getLogger(RecipePayInfoCallBackService.class);

    @Autowired
    private RemoteRecipeOrderService recipeOrderService;
    @Autowired
    private RemoteRecipeLogService recipeLogService;
    @Autowired
    private RedisClient redisClient;
    @Autowired
    private OrderManager orderManager;
    @Autowired
    private RecipeOrderPayFlowManager recipeOrderPayFlowManager;
    @Autowired
    private IConfigurationClient configurationClient;

    @Override
    @RpcService
    public boolean doHandleAfterPay(PayResultDTO payResult) {
        logger.info("doBusinessAfterOrderSuccess payResult[{}]", JSONUtils.toString(payResult));
        Integer busId = payResult.getBusId();
        String outTradeNo = payResult.getOutTradeNo();
        String tradeNo = payResult.getTradeNo();

        RecipeOrderBean order = recipeOrderService.get(busId);
        if (null == order) {
            logger.info("doBusinessAfterOrderSuccess busObject not exists, busId[{}]", busId);
            return false;
        }
        //已处理-幂等判断
        if (order.getPayFlag() != null && order.getPayFlag() == 1) {
            logger.info("doBusinessAfterOrderSuccess payflag has been set true, busId[{}]", busId);
            return true;
        }
        //已取消不做更新
        if (order.getStatus() == 8 || order.getStatus() == 7) {
            logger.info("doBusinessAfterOrderSuccess effective is 0, busId[{}]", busId);
            return true;
        }
        HashMap<String, Object> attr = new HashMap<>();
        attr.put("tradeNo", tradeNo);
        attr.put("outTradeNo", outTradeNo);
        Map<String, String> notifyMap = payResult.getNotifyMap();
        //组装卫宁付返回参数并更新订单
        assembleWeiningPayCallBackParamAndUpdate(busId, notifyMap, attr, order);

        String orderCode = order.getOrderCode();
        //保存电子票据
        saveRecipeOrderBill(notifyMap, orderCode);

        //业务支付回调
        if (StringUtils.isNotEmpty(orderCode)) {
            IRecipeService recipeService = RecipeAPI.getService(IRecipeService.class);
            RecipeBean recipeBean = recipeService.getRecipeByOrderCode(orderCode);
            Integer payMode = PayModeGiveModeUtil.getPayMode(order.getPayMode(), recipeBean.getGiveMode());
            recipeOrderService.finishOrderPay(order.getOrderCode(), PayConstant.PAY_FLAG_PAY_SUCCESS, payMode);
        } else {
            recipeOrderService.finishOrderPay(order.getOrderCode(), PayConstant.PAY_FLAG_PAY_SUCCESS, RecipeConstant.PAYMODE_ONLINE);
        }

        //更新处方支付日志
        String memo = "订单: 收到支付平台支付消息 商户订单号:" + outTradeNo + ",第三方流水号：" + tradeNo;
        updateRecipePayLog(order, memo);
        //存在优惠券的处理
        if (ValidateUtil.notNullAndZeroInteger(order.getCouponId()) && order.getCouponId() != -1) {
            ICouponBaseService couponService = AppContextHolder.getBean("voucher.couponBaseService", ICouponBaseService.class);
            couponService.useCouponById(order.getCouponId());
        }
        //为邵逸夫添加药品费用的支付流水
        Boolean syfPayMode = configurationClient.getValueBooleanCatch(order.getOrganId(), "syfPayMode",false);
        if (syfPayMode) {
            try {
                RecipeOrderPayFlow recipeOrderPayFlow = recipeOrderPayFlowManager.getByOrderIdAndType(busId, PayFlowTypeEnum.RECIPE_FLOW.getType());
                logger.info("recipeOrderPayFlow :{}.", JSONUtils.toString(recipeOrderPayFlow));
                if (null == recipeOrderPayFlow) {
                    recipeOrderPayFlow = new RecipeOrderPayFlow();
                    recipeOrderPayFlow.setOrderId(order.getOrderId());
                    if (null != notifyMap && notifyMap.get("total_amount") != null) {
                        recipeOrderPayFlow.setTotalFee(Double.parseDouble(payResult.getNotifyMap().get("total_amount")));
                    }
                    recipeOrderPayFlow.setPayFlowType(PayFlowTypeEnum.RECIPE_FLOW.getType());
                    recipeOrderPayFlow.setPayFlag(PayFlagEnum.PAYED.getType());
                    recipeOrderPayFlow.setOutTradeNo(payResult.getOutTradeNo());
                    recipeOrderPayFlow.setPayOrganId(payResult.getPayOrganId());
                    recipeOrderPayFlow.setTradeNo(payResult.getTradeNo());
                    recipeOrderPayFlow.setWnPayWay("");
                    recipeOrderPayFlow.setWxPayWay(payResult.getPayWay());
                    recipeOrderPayFlowManager.save(recipeOrderPayFlow);
                } else {
                    recipeOrderPayFlow.setPayFlag(PayFlagEnum.PAYED.getType());
                    recipeOrderPayFlow.setOutTradeNo(outTradeNo);
                    recipeOrderPayFlow.setTradeNo(tradeNo);
                    recipeOrderPayFlow.setPayOrganId(payResult.getPayOrganId());
                    if (notifyMap != null && notifyMap.get("total_amount") != null) {
                        Double payBackPrice = ConversionUtils.convert(notifyMap.get("total_amount"), Double.class);
                        recipeOrderPayFlow.setTotalFee(payBackPrice);
                    }
                    recipeOrderPayFlowManager.updateNonNullFieldByPrimaryKey(recipeOrderPayFlow);
                }
            } catch (Exception e) {
                logger.error("error :" ,e);
            }
        }
        return true;
    }

    @Override
    public boolean doHandleAfterPayFail(PayResultDTO payResult) {
        logger.info("RecipePayInfoCallBackService doHandleAfterPayFail payResult:{}.", JSON.toJSONString(payResult));
        RecipeOrder recipeOrder = orderManager.getByOutTradeNo(payResult.getOutTradeNo());
        if (null != recipeOrder && StringUtils.isNotEmpty(payResult.getFailMessage())) {
            recipeOrder.setCancelReason(payResult.getFailMessage());
            return orderManager.updateNonNullFieldByPrimaryKey(recipeOrder);
        }
        return false;
    }

    /**
     * 更新处方支付日志
     *
     * @param order
     */
    private void updateRecipePayLog(RecipeOrderBean order, String memo) {
        List<Integer> recipeIdList = null;
        if (StringUtils.isNotEmpty(order.getRecipeIdList())) {
            recipeIdList = JSONUtils.parse(order.getRecipeIdList(), List.class);
        }
        if (CollectionUtils.isNotEmpty(recipeIdList)) {
            for (int i = 0; i < recipeIdList.size(); i++) {
                recipeLogService.saveRecipeLog(recipeIdList.get(i), RecipeStatusConstant.UNKNOW, RecipeStatusConstant.UNKNOW, memo);
            }
        }
    }

    /**
     * 组装卫宁付返回参数并更新订单信息
     *
     * @param busId
     * @param notifyMap
     * @param attr
     * @param order
     */
    private void assembleWeiningPayCallBackParamAndUpdate(Integer busId, Map<String, String> notifyMap, HashMap<String, Object> attr, RecipeOrderBean order) {
        String pharmNo = null;
        //卫宁付返回信息 保存到处方订单表中
        if (notifyMap != null && notifyMap.get("total_amount") != null) {
            //支付金额---自费金额
            Double payBackPrice = ConversionUtils.convert(notifyMap.get("total_amount"), Double.class);
            //医保出参(base64编码后的需要解码)
            String medicalSettleInfo = StringUtils.defaultString(notifyMap.get("ybcc"), "");
            //医保代码
            String medicalSettleCode = StringUtils.defaultString(notifyMap.get("ybdm"), "");
            //医保类型的确定    0是自费，1是医保
            String med_type = StringUtils.defaultString(notifyMap.get("med_type"), "");
            //支付方式
            String wnPayWay = StringUtils.defaultString(notifyMap.get("zffs"), "");
            //支付业务信息
            String bodyString = StringUtils.defaultString(notifyMap.get("body"), "");

            // 医保结算内容
            String ybbody = StringUtils.defaultString(notifyMap.get("ybbody"), "");
            if(StringUtils.isNotEmpty(ybbody)){
                attr.put("healthInsurancePayContent", ybbody);
            }
            // 商保结算内容
            String sbbody = StringUtils.defaultString(notifyMap.get("sbbody"), "");
            Map<String, String> sbbodyMap = JSONUtils.parse(sbbody, Map.class);
            if(MapUtils.isNotEmpty(sbbodyMap)){
                Integer type = ConversionUtils.convert(sbbodyMap.get("type"), Integer.class);
                Double sbjmje = ConversionUtils.convert(sbbodyMap.get("sbjmje"), Double.class);
                attr.put("thirdPayType", type);
                attr.put("thirdPayFee", sbjmje);
            }

            Map<String, String> body = JSONUtils.parse(bodyString, Map.class);
            //获取平台的ysbody---卫宁付支付预算信息
            String ysbodyString = StringUtils.defaultString(notifyMap.get("ysbody"), "");
            Map<String, String> ysbody = JSONUtils.parse(ysbodyString, Map.class);
            //一些信息先从body取，取不到再从ysbody取（兼容）
            if (body != null) {
                //取药窗口
                pharmNo = StringUtils.defaultString(body.get("fyyfjh"), "");
                //支付结算信息
                String payBackInfo = StringUtils.defaultString(body.get("memo"), "");
                //设置his收据号
                String hisSettlementNo = StringUtils.defaultString(body.get("sjh"), "");
                attr.put("hisSettlementNo", hisSettlementNo);
                attr.put("payBackInfo", payBackInfo);

                //设置总金额---替换预结算返回的
                if (body.get("zje") != null) {
                    Double zje = ConversionUtils.convert(body.get("zje"), Double.class);
                    attr.put("preSettleTotalAmount", zje);
                }

                if (body.get("ybzf") != null) {
                    //设置医保支付金额
                    Double ybzf = ConversionUtils.convert(body.get("ybzf"), Double.class);
                    attr.put("fundAmount", ybzf);
                }
                //替换预结算返回的自费金额
                if (null != ysbody && ysbody.get("yfje") != null) {
                    attr.put("cashAmount", ConversionUtils.convert(ysbody.get("yfje"), Double.class));
                }
            }

            //医保信息逻辑优化
            if (ysbody != null) {
                //总金额
                Double zje = attr.get("zje") != null ? ConversionUtils.convert(attr.get("zje"), Double.class) : ConversionUtils.convert(ysbody.get("zje"), Double.class);
                attr.put("preSettleTotalAmount", zje);

                //医保支付金额
                Double ybzf = attr.get("ybzf") != null ? ConversionUtils.convert(attr.get("ybzf"), Double.class) : ConversionUtils.convert(ysbody.get("ybzf"), Double.class);
                attr.put("fundAmount", ybzf);
                //替换预结算返回的自费金额
                if (ysbody.get("yfje") != null) {
                    attr.put("cashAmount", ConversionUtils.convert(ysbody.get("yfje"), Double.class));
                }
                try {
                    //计算预结算返回的总金额与平台的总金额是否一致，如果不一致，则更新
                    if (null != zje && null != order.getTotalFee() && order.getTotalFee().doubleValue() != zje) {
                        logger.info("卫宁返回预结算金额与订单平台金额不一致,zje:{},totalFee:{},expressFeePayWay:{},expressFee：{}", zje, order.getTotalFee(), order.getExpressFeePayWay(), order.getExpressFee());
                        if (null == order.getExpressFeePayWay() || ExpressFeePayWayEnum.ONLINE.getType().equals(order.getExpressFeePayWay())) {
                            //表示快递费是线上支付
                            if (null != order.getExpressFee() && order.getExpressFee().compareTo(BigDecimal.ZERO) > 0) {
                                //说明快递费线上支付取费用大于0
                                double recipeFee = zje - order.getExpressFee().doubleValue();
                                if (recipeFee >= 0) {
                                    attr.put("RecipeFee", new BigDecimal(recipeFee));
                                    attr.put("TotalFee", new BigDecimal(zje));
                                    attr.put("ActualPrice", new BigDecimal(zje));
                                }
                            } else {
                                attr.put("RecipeFee", new BigDecimal(zje));
                                attr.put("TotalFee", new BigDecimal(zje));
                                attr.put("ActualPrice", new BigDecimal(zje));
                            }
                        } else {
                            //表示快递费线下支付
                            if (null != order.getExpressFee() && order.getExpressFee().compareTo(BigDecimal.ZERO) > 0) {
                                double totalFee = zje + order.getExpressFee().doubleValue();
                                attr.put("TotalFee", new BigDecimal(totalFee));
                                attr.put("RecipeFee", new BigDecimal(zje));
                                attr.put("ActualPrice", new BigDecimal(zje));
                            } else {
                                attr.put("RecipeFee", new BigDecimal(zje));
                                attr.put("TotalFee", new BigDecimal(zje));
                                attr.put("ActualPrice", new BigDecimal(zje));
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.error("设置订单金额失败", e);
                }
            }

            attr.put("PayBackPrice", payBackPrice);
            try {
                medicalSettleInfo = new String(Base64.decode(medicalSettleInfo, 1));
                if (StringUtils.isNotEmpty(medicalSettleInfo) && medicalSettleInfo.length() < 1500) {
                    attr.put("medicalSettleInfo", medicalSettleInfo);
                }
            } catch (Exception e) {
                logger.error("doBusinessAfterOrderSuccess error busId={}", busId);
            }
            attr.put("medicalSettleCode", medicalSettleCode);
            attr.put("WnPayWay", wnPayWay);
            //支付平台返回的医保类型0是自费，1是医保
            if (StringUtils.isNotEmpty(med_type)) {
                Integer medType = ConversionUtils.convert(med_type, Integer.class);
                //只有处方订单是自费订单的时候才保存
                if (medType == 1 && order.getOrderType() == 0) {
                    //存储到处方是4-普通医保
                    attr.put("orderType", 4);
                }
            }
        }
        //更新订单信息
        recipeOrderService.updateOrderInfo(order.getOrderCode(), attr);
        if (StringUtils.isNotEmpty(order.getRecipeIdList())) {
            List<Integer> recipeIdList = JSONUtils.parse(order.getRecipeIdList(), List.class);
            //更新窗口号
            if (StringUtils.isNotEmpty(pharmNo)) {
                recipeOrderService.updateExtPharmNoS(recipeIdList, pharmNo);
            }
        }
    }

    /**
     * 保存订单电子票据
     *
     * @param notifyMap
     * @param orderCode
     */
    private void saveRecipeOrderBill(Map<String, String> notifyMap, String orderCode) {
        try {
            if (notifyMap != null) {
                String bodyString = StringUtils.defaultString(notifyMap.get("body"), "");
                Map<String, String> body = JSONUtils.parse(bodyString, Map.class);
                if (body != null && StringUtils.isNotBlank(body.get("fph"))) {
                    RecipeOrderBillReqTO billReqTO = new RecipeOrderBillReqTO();
                    billReqTO.setRecipeOrderCode(orderCode);
                    billReqTO.setBillBathCode(StringUtils.defaultString(body.get("dzsjh"), ""));
                    billReqTO.setBillNumber(StringUtils.defaultString(body.get("fph"), ""));
                    billReqTO.setBillQrCode(StringUtils.defaultString(body.get("dzpewm"), ""));
                    billReqTO.setBillPictureUrl(StringUtils.defaultString(body.get("dzymurl"), ""));
                    logger.info("支付成功后保存订单电子票据信息={}", JSONObject.toJSONString(billReqTO));
                    recipeOrderService.saveRecipeOrderBill(billReqTO);
                }
            }
        } catch (Exception e) {
            logger.error("支付成功后保存订单电子票据异常，error=", e);
        }
    }

    @Override
    @RpcService
    public boolean doHandleAfterRefund(Order order, int targetPayflag, Map<String, String> refundResult) {
        logger.info("doHandleAfterRefund order={},targetPayflag={},refundResult={}",JSONArray.toJSONString(order),targetPayflag, JSONArray.toJSONString(refundResult));
        // 处方
        RecipeOrderBean recipeOrderBean = recipeOrderService.getByOutTradeNo(order.getOutTradeNo());

        //接口组存在同步和异步两种方式调用该接口，导致上传监管平台信息重复，故做幂等性判断
        //判断是否存在分布式锁
        String outTradeNo = recipeOrderBean.getOutTradeNo();
        Integer payFlag = recipeOrderBean.getPayFlag();
        if(StringUtils.isNotEmpty(outTradeNo)){
            String lockKey = CacheConstant.KEY_PAY_REFUND_LOCK + outTradeNo;
            boolean unlock = lock(lockKey);
            if (unlock) {
                //加锁成功：根据数据库查询到的信息做幂等
                if(new Integer(3).equals(payFlag) || new Integer(4).equals(payFlag)){
                    return true;
                }
            } else {
                //加锁失败：说明该退费申请已调用过
                return true;
            }
        }

        recipeOrderService.finishOrderPayByRefund(recipeOrderBean.getOrderCode(), targetPayflag, RecipeConstant.PAYMODE_ONLINE,order.getRefundNo());
        StringBuilder memo = new StringBuilder("订单=" + recipeOrderBean.getOrderCode() + " ");
        switch (targetPayflag) {
            case 3:
                memo.append("退款成功");
                break;
            case 4:
                memo.append("退款失败");
                break;
            default:
                memo.append("支付 未知状态，payflag:" + targetPayflag);
                break;
        }
        if (StringUtils.isNotEmpty(recipeOrderBean.getRecipeIdList())) {
            List<Integer> recipeIdList = JSONUtils.parse(recipeOrderBean.getRecipeIdList(), List.class);
            if (CollectionUtils.isNotEmpty(recipeIdList)) {
                Integer bussId = recipeIdList.get(0);
                //调用回调处方退费
                recipeOrderService.refundCallback(bussId, targetPayflag, null, PayBusTypeEnum.RECIPE_BUS_TYPE.getType());
            }
        }
        //更新处方日志
        updateRecipePayLog(recipeOrderBean, memo.toString());
        return true;
    }

    private boolean lock(String lockKey) {
        return redisClient.setNX(lockKey, "true") && redisClient.setex(lockKey, 30L);
    }
}
