package recipe.dao;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.ngari.recipe.dto.RecipeOrderRefundReqDTO;
import com.ngari.recipe.dto.RegulationChargeDetailDTO;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.pay.model.BusBillDateAccountDTO;
import com.ngari.recipe.recipe.model.RecipeOrderDetailExportDTO;
import com.ngari.recipe.recipereportform.model.*;
import ctd.persistence.annotation.DAOMethod;
import ctd.persistence.annotation.DAOParam;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.HibernateSupportDelegateDAO;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.annotation.RpcSupportDAO;
import ctd.util.converter.ConversionUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.dao.bean.BillBusFeeBean;
import recipe.dao.bean.BillDrugFeeBean;
import recipe.dao.bean.BillRecipeDetailBean;
import recipe.dao.bean.RecipeBillBean;
import recipe.dao.comment.ExtendDao;
import recipe.enumerate.status.PayWayEnum;
import recipe.enumerate.status.RecipeOrderStatusEnum;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * @date:2017/2/13.
 */
@RpcSupportDAO
public abstract class RecipeOrderDAO extends HibernateSupportDelegateDAO<RecipeOrder> implements ExtendDao<RecipeOrder> {
    private static final Logger logger = LoggerFactory.getLogger(RecipeOrderDAO.class);

    private static final Map<Integer, String> DRUG_TYPE_TABLE = ImmutableMap.of(1, "西药", 2, "中成药", 3, "中药", 4, "膏方");

    public RecipeOrderDAO() {
        super();
        this.setEntityName(RecipeOrder.class.getName());
        this.setKeyField("orderId");
    }

    @Override
    public boolean updateNonNullFieldByPrimaryKey(RecipeOrder recipeOrder) {
        return updateNonNullFieldByPrimaryKey(recipeOrder, "orderId");
    }

    /**
     * 根据编号获取有效订单
     *
     * @param orderCode
     * @return
     */
    @DAOMethod(sql = "from RecipeOrder where orderCode=:orderCode")
    public abstract RecipeOrder getByOrderCode(@DAOParam("orderCode") String orderCode);

    @DAOMethod(sql = "update RecipeOrder set trackingNumber=:trackingNumber where orderCode=:orderCode")
    public abstract void updateTrackingNumberByOrderCode(@DAOParam("orderCode") String orderCode,
                                                         @DAOParam("trackingNumber") String trackingNumber);

    /**
     * 更新药企推送成功标志
     * @param orderCode
     * @param pushFlag
     */
    @DAOMethod(sql = "update RecipeOrder set pushFlag=:pushFlag where orderCode=:orderCode")
    public abstract void updatePushFlagByOrderCode(@DAOParam("orderCode") String orderCode,
                                                         @DAOParam("pushFlag") Integer pushFlag);

    /**
     * 获取订单
     *
     * @param orderId          订单ID
     * @param logisticsCompany 快递公司
     * @param trackingNumber   快递单号
     * @return
     */
    @DAOMethod(sql = "from RecipeOrder where orderId =:orderId and logisticsCompany=:logisticsCompany and trackingNumber =:trackingNumber")
    public abstract RecipeOrder getByLogisticsCompanyAndTrackingNumber(@DAOParam("orderId") Integer orderId,
                                                                       @DAOParam("logisticsCompany") Integer logisticsCompany,
                                                                       @DAOParam("trackingNumber") String trackingNumber);

    /**
     * 批量查询 根据编号获取有效订单
     *
     * @param orderCodeList
     * @return
     */
    @DAOMethod(sql = "from RecipeOrder where orderCode in (:orderCodeList)")
    public abstract List<RecipeOrder> findByOrderCode(@DAOParam("orderCodeList") Collection<String> orderCodeList);

    /**
     * 根据流水号获取订单
     *
     * @param tradeNo
     * @return
     */
    @DAOMethod
    public abstract RecipeOrder getByOutTradeNo(String tradeNo);

    /**
     * 根据订单号获取订单信息
     *
     * @param orderId
     * @return
     */
    @DAOMethod
    public abstract RecipeOrder getByOrderId(Integer orderId);

    /**
     * 根据传芳id获取订单编号
     *
     * @param recipeId
     * @return
     */
    @DAOMethod(sql = "select order.orderCode from RecipeOrder order, Recipe recipe where order.orderCode=recipe.orderCode and order.effective=1 and recipe.recipeId=:recipeId")
    public abstract String getOrderCodeByRecipeId(@DAOParam("recipeId") Integer recipeId);

    /**
     * 根据处方id获取订单编号
     *
     * @param recipeId
     * @return
     */
    @DAOMethod(sql = "select order.orderCode from RecipeOrder order, Recipe recipe where order.orderCode=recipe.orderCode and recipe.recipeId=:recipeId")
    public abstract String getOrderCodeByRecipeIdWithoutCheck(@DAOParam("recipeId") Integer recipeId);

    /**
     * 根据处方id获取订单
     *
     * @param recipeId
     * @return
     */
    @DAOMethod(sql = "select order from RecipeOrder order, Recipe recipe where order.orderCode=recipe.orderCode and order.effective=1 and recipe.recipeId=:recipeId")
    public abstract RecipeOrder getOrderByRecipeId(@DAOParam("recipeId") Integer recipeId);

    /**
     * 根据处方id获取订单
     *
     * @param recipeId 处方ID
     * @return 订单
     */
    @DAOMethod(sql = "select order from RecipeOrder order, Recipe recipe where order.orderCode=recipe.orderCode and recipe.recipeId=:recipeId")
    public abstract RecipeOrder getRecipeOrderByRecipeId(@DAOParam("recipeId") Integer recipeId);

    /**
     * 根据处方id获取药企id
     *
     * @param recipeId
     * @return
     */
    @DAOMethod(sql = "select order.enterpriseId from RecipeOrder order, Recipe recipe where order.orderCode=recipe.orderCode and recipe.recipeId=:recipeId")
    public abstract Integer getEnterpriseIdByRecipeId(@DAOParam("recipeId") Integer recipeId);

    /**
     * 根据处方id批量删除
     *
     * @param recipeIds
     */
    @DAOMethod(sql = "delete from RecipeOrder where orderCode in (:orderCodeList)")
    public abstract void deleteByRecipeIds(@DAOParam("orderCodeList") List<String> orderCodeList);

    /**
     * 根据支付标识查询订单集合
     *
     * @param payFlag
     * @return
     */
    @DAOMethod
    public abstract List<RecipeOrder> findByPayFlag(Integer payFlag);

    /**
     * 订单是否有效
     *
     * @param orderCode
     * @return
     */
    public boolean isEffectiveOrder(final String orderCode) {
        if (StringUtils.isEmpty(orderCode)) {
            return false;
        }

        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select count(1) from RecipeOrder where orderCode=:orderCode ");
                hql.append(" and effective=1 ");
                Query q = ss.createQuery(hql.toString());
                q.setParameter("orderCode", orderCode);

                long count = (Long) q.uniqueResult();
                setResult(count > 0);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 更新订单自定义字段
     *
     * @param orderCode
     * @param changeAttr
     * @return
     */
    public Boolean updateByOrdeCode(final String orderCode, final Map<String, ?> changeAttr) {
        if (null == changeAttr || changeAttr.isEmpty()) {
            return true;
        }

        HibernateStatelessResultAction<Boolean> action = new AbstractHibernateStatelessResultAction<Boolean>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("update RecipeOrder set ");
                StringBuilder keyHql = new StringBuilder();
                for (String key : changeAttr.keySet()) {
                    keyHql.append("," + key + "=:" + key);
                }
                hql.append(keyHql.toString().substring(1)).append(" where orderCode=:orderCode");
                Query q = ss.createQuery(hql.toString());

                q.setParameter("orderCode", orderCode);
                for (String key : changeAttr.keySet()) {
                    q.setParameter(key, changeAttr.get(key));
                }

                int flag = q.executeUpdate();
                setResult(flag == 1);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据需要变更的状态获取处方ID集合
     *
     * @param startDt
     * @param endDt
     * @return
     */
    public List<String> getRecipeIdForCancelRecipeOrder(final String startDt, final String endDt) {
        HibernateStatelessResultAction<List<String>> action = new AbstractHibernateStatelessResultAction<List<String>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "select orderCode from RecipeOrder where createTime between '" + startDt + "' and '" + endDt + "' and status not in (6,7,8) and drugStoreCode is not null";

                Query q = ss.createQuery(sql);
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据处方关联的订单
     *
     * @param recipeId
     * @return
     */
    @DAOMethod(sql = "select order from RecipeOrder order, Recipe recipe where order.orderCode=recipe.orderCode and recipe.recipeId=:recipeId")
    public abstract RecipeOrder getRelationOrderByRecipeId(@DAOParam("recipeId") Integer recipeId);

    /**
     * 根据物流单号查询手机号
     *
     * @param trackingNumber 顺丰物流单号
     * @return 订单信息
     */
    @DAOMethod(sql = "from RecipeOrder where LogisticsCompany = 1 and  trackingNumber =:trackingNumber")
    public abstract RecipeOrder getByTrackingNumber(@DAOParam("trackingNumber") String trackingNumber);

    /**
     * 根据日期查询订单支付和退款信息(只获取实际支付金额不为0的，调用支付平台的)
     *
     * @param startTime
     * @param endTime
     * @param start
     * @param pageSize
     * @return
     */
    public List<BillRecipeDetailBean> getPayAndRefundInfoByTime(Date startTime, Date endTime, int start, int pageSize) {
        HibernateStatelessResultAction<List<BillRecipeDetailBean>> action = new AbstractHibernateStatelessResultAction<List<BillRecipeDetailBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("select * from ( ");
                hql.append("select r.recipeId, r.doctor, o.MpiId, o.PayTime, o.OrganId, r.Depart, o.OutTradeNo, ");
                hql.append("o.OrderType, r.GiveMode, o.PayFlag, o.RegisterFee, o.ExpressFee, o.DecoctionFee, o.AuditFee, ");
                hql.append("o.OtherFee, o.RecipeFee, o.CouponFee, o.PayBackPrice, o.FundAmount, d.name, 0 as billType, o.EnterpriseId, r.recipeCode from ");
                hql.append("cdr_recipe r INNER JOIN cdr_recipeorder o on r.OrderCode = o.OrderCode LEFT JOIN cdr_drugsenterprise d on d.id = o.EnterpriseId ");
                hql.append("where o.payFlag = 1 and o.payTime between :startTime and :endTime and o.Effective = 1 and o.actualPrice <> 0 ");
                hql.append("UNION ALL ");
                hql.append("select r.recipeId, r.doctor, o.MpiId, o.refundTime as PayTime, o.OrganId, r.Depart, o.OutTradeNo, ");
                hql.append("o.OrderType, r.GiveMode, o.PayFlag, o.RegisterFee, o.ExpressFee, o.DecoctionFee, o.AuditFee, ");
                hql.append("o.OtherFee, o.RecipeFee, o.CouponFee, o.PayBackPrice, o.FundAmount, d.name, 1 as billType, o.EnterpriseId, r.recipeCode from ");
                hql.append("cdr_recipe r INNER JOIN cdr_recipeorder o on r.OrderCode = o.OrderCode LEFT JOIN cdr_drugsenterprise d on d.id = o.EnterpriseId ");
                hql.append("where (o.refundFlag is Not Null and o.refundFlag <> 0) and o.refundTime between :startTime and :endTime and o.actualPrice <> 0 ");
                hql.append("UNION ALL ");
                hql.append("select r.recipeId, r.doctor, o.MpiId, o.PayTime, o.OrganId, r.Depart, o.OutTradeNo, ");
                hql.append("o.OrderType, r.GiveMode, o.PayFlag, o.RegisterFee, o.ExpressFee, o.DecoctionFee, o.AuditFee, ");
                hql.append("o.OtherFee, o.RecipeFee, o.CouponFee, o.PayBackPrice, o.FundAmount, d.name, 0 as billType, o.EnterpriseId, r.recipeCode from  ");
                hql.append("cdr_recipe r INNER JOIN cdr_recipeorder o on r.OrderCode = o.OrderCode LEFT JOIN cdr_drugsenterprise d on d.id = o.EnterpriseId ");
                hql.append("where (o.refundFlag is Not Null and o.refundFlag <> 0) and o.payFlag <>1 and o.payTime between :startTime and :endTime and o.actualPrice <> 0 ");
                hql.append(" ) a order by a.recipeId, a.payTime");

                Query q = ss.createSQLQuery(hql.toString());
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                q.setFirstResult(start);
                q.setMaxResults(pageSize);
                List<Object[]> result = q.list();
                List<BillRecipeDetailBean> backList = new ArrayList<>(pageSize);
                if (CollectionUtils.isNotEmpty(result)) {
                    BillRecipeDetailBean vo;
                    for (Object[] objs : result) {
                        vo = new BillRecipeDetailBean();
                        vo.setRecipeId(objs[2] == null ? null : (Integer) objs[0]);
                        vo.setMpiId(objs[2] == null ? null : objs[2] + "");
                        vo.setDoctorId(objs[1] == null ? null : (Integer) objs[1]);
                        vo.setRecipeTime(objs[3] == null ? null : (Date) objs[3]);
                        vo.setOrganId(objs[4] == null ? null : (Integer) objs[4]);
                        vo.setDeptId(objs[5] == null ? null : (Integer) objs[5]);
                        vo.setOutTradeNo(objs[6] == null ? null : objs[6] + "");
                        vo.setSettleType(objs[7] == null ? null : Integer.parseInt(objs[7] + ""));
                        vo.setDeliveryMethod(objs[8] == null ? null : Integer.parseInt(objs[8] + ""));
                        vo.setDrugCompany(objs[21] == null ? null : (Integer) objs[21]);
                        vo.setDrugCompanyName(objs[19] == null ? null : objs[19] + "");
                        vo.setPayFlag(objs[9] == null ? null : Integer.parseInt(objs[9] + ""));
                        vo.setAppointFee(objs[10] == null ? null : Double.valueOf(objs[10] + ""));
                        vo.setDeliveryFee(objs[11] == null ? null : Double.valueOf(objs[11] + ""));
                        vo.setDaiJianFee(objs[12] == null ? null : Double.valueOf(objs[12] + ""));
                        vo.setReviewFee(objs[13] == null ? null : Double.valueOf(objs[13] + ""));
                        vo.setOtherFee(objs[14] == null ? null : Double.valueOf(objs[14] + ""));
                        vo.setDrugFee(objs[15] == null ? null : Double.valueOf(objs[15] + ""));
                        vo.setDicountedFee(objs[16] == null ? null : Double.valueOf(objs[16] + ""));
                        vo.setTotalFee(objs[17] == null ? null : Double.valueOf(objs[17] + ""));
                        vo.setMedicarePay(objs[18] == null ? null : Double.valueOf(objs[18] + ""));
                        vo.setBillType(objs[20] == null ? null : Integer.parseInt(objs[20] + ""));
                        vo.setSelfPay(objs[17] == null ? 0.0 : new BigDecimal(objs[17] + "").subtract(new BigDecimal(objs[18] == null ? "0.0" : objs[18] + "")).doubleValue());
                        vo.setHisRecipeId(objs[22] == null ? null : objs[22].toString());
                        backList.add(vo);
                    }
                }
                setResult(backList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据药企编号和支付时间查询订单
     *
     * @param enterpriseIds 药企编号
     * @param payTime       支付时间
     * @return 订单列表
     */
    public List<RecipeOrder> findRecipeOrderByDepIdAndPayTime(List<Integer> enterpriseIds, String payTime) {
        HibernateStatelessResultAction<List<RecipeOrder>> action = new AbstractHibernateStatelessResultAction<List<RecipeOrder>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "select a from RecipeOrder a,Recipe b where a.orderCode = b.orderCode and b.pushFlag = 0 and a.payFlag = 1 and a.effective = 1 and a.status in (2,3,12)" +
                        " and a.effective = 1 and a.enterpriseId in (:enterpriseIds)";

                Query q = ss.createQuery(sql);
                q.setParameterList("enterpriseIds", enterpriseIds);
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据日期查询订单支付和退款信息(只获取实际支付金额不为0的，调用支付平台的)
     *
     * @param ngariOrganIds
     * @param startTime
     * @param endTime
     * @return
     */
    public List<RegulationChargeDetailDTO> queryRegulationChargeDetailList(final List<Integer> ngariOrganIds, final Date startTime, final Date endTime) {
        HibernateStatelessResultAction<List<RegulationChargeDetailDTO>> action = new AbstractHibernateStatelessResultAction<List<RegulationChargeDetailDTO>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                hql.append("select r.ClinicOrgan,l.RecipeDetailID,o.PayFlag,r.ClinicID,o.TradeNo,r.RecipeID,r.RecipeType,l.DrugUnit,l.actualSalePrice,l.UseTotalDose,r.Status,drug.medicalDrugCode,l.salePrice from cdr_recipe r LEFT JOIN cdr_recipedetail l ON r.RecipeID = l.recipeId");
                hql.append(" LEFT JOIN base_organdruglist drug on drug.OrganDrugCode=l.OrganDrugCode and drug.OrganID=r.clinicorgan");
                hql.append(" LEFT JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode");
                hql.append(" WHERE (date(r.CreateDate) between :startTime and :endTime OR date(r.LastModify) between :startTime and :endTime)");
                hql.append(" AND o.Effective = 1");
                hql.append(" AND o.PayFlag > 0");
                hql.append(" AND r.bussSource = 2");
                hql.append(" AND r.ClinicOrgan IN :ngariOrganIds");
                hql.append(" AND l.`Status` =1");
                Query q = ss.createSQLQuery(hql.toString());
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                q.setParameterList("ngariOrganIds", ngariOrganIds);
                logger.info("paramter is startTime:[{}],endTime:[{}],ngariOrganIds[{}]", startTime, endTime, ngariOrganIds);
                List<Object[]> result = q.list();
                List<RegulationChargeDetailDTO> backList = new ArrayList<>();
                if (CollectionUtils.isNotEmpty(result)) {
                    RegulationChargeDetailDTO vo;
                    for (Object[] objs : result) {
                        vo = new RegulationChargeDetailDTO();
                        vo.setOrganID(objs[0] == null ? null : (Integer) objs[0]);
                        vo.setRecipeDetailID(objs[1] == null ? null : objs[1] + "");
                        vo.setPayFlag(objs[2] == null ? null : Integer.parseInt(objs[2] + ""));
                        vo.setClinicID(objs[3] == null ? null : objs[3] + "");
                        vo.setTradeNo(objs[4] == null ? null : objs[4] + "");
                        vo.setRecipeID(objs[5] == null ? null : objs[5] + "");
                        vo.setRecipeType(objs[6] == null ? null : Integer.parseInt(objs[6] + ""));
                        vo.setDrugUnit(objs[7] == null ? null : objs[7] + "");
                        vo.setActualSalePrice(objs[8] == null ? null : (BigDecimal) objs[8]);
                        vo.setUseTotalDose(objs[9] == null ? null : (BigDecimal) objs[9]);
                        vo.setStatus(objs[10] == null ? null : Integer.parseInt(objs[10] + ""));
                        vo.setMedicalDrugCode(objs[11] == null ? null : objs[11] + "");
                        vo.setSalePrice(objs[12] == null ? null : (BigDecimal) objs[12]);
                        backList.add(vo);
                    }
                }
                setResult(backList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


    /**
     * 根据物流公司编号与快递单号查询订单编号
     *
     * @param logisticsCompany
     * @param trackingNumber
     * @return
     */
    @DAOMethod(sql = "select orderCode from RecipeOrder order  where order.logisticsCompany=:logisticsCompany and order.trackingNumber=:trackingNumber")
    public abstract String getOrderCodeByLogisticsCompanyAndTrackingNumber(@DAOParam("logisticsCompany") Integer logisticsCompany,
                                                                           @DAOParam("trackingNumber") String trackingNumber);

    /**
     * 根据日期获取电子处方药企配送订单明细
     *
     * @param startTime 开始时间
     * @param endTime   截止时间
     * @param organId   机构ID
     * @param depId     药企ID
     * @return
     */
    public List<Map<String, Object>> queryrecipeOrderDetailed(Date startTime, Date endTime, Integer organId, List<Integer> organIds, Integer depId, Integer drugId, String orderColumn, String orderType, Integer recipeId, Integer payType, int start, int limit) {
        HibernateStatelessResultAction<List<Map<String, Object>>> action = new AbstractHibernateStatelessResultAction<List<Map<String, Object>>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder sql = new StringBuilder();
                StringBuilder sqlPay = new StringBuilder();
                StringBuilder sqlRefund = new StringBuilder();
                if (drugId != null) {
                    sqlPay.append("SELECT r.recipeId, r.patientName, r.MPIID, dep.NAME, r.organName, r.doctorName, r.SignDate as signDate, '支付成功' as payType, o.PayTime as payTime, o.refundTime as refundTime, d.useTotalDose as dose, IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) * d.useTotalDose as ActualPrice");
                    sqlPay.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID LEFT JOIN cdr_drugsenterprise dep ON o.EnterpriseId = dep.Id ");
                    sqlPay.append(" WHERE r.GiveMode = 1 and ((o.payflag = 1 OR o.refundflag = 1) and o.paytime BETWEEN :startTime  AND :endTime ) ");
                    sqlRefund.append("SELECT r.recipeId, r.patientName, r.MPIID, dep.NAME, r.organName, r.doctorName, r.SignDate as signDate, '退款成功' as payType, o.PayTime as payTime, o.refundTime as refundTime, d.useTotalDose as dose, IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) * (0-d.useTotalDose) as ActualPrice");
                    sqlRefund.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID LEFT JOIN cdr_drugsenterprise dep ON o.EnterpriseId = dep.Id ");
                    sqlRefund.append(" WHERE r.GiveMode = 1 and (o.refundflag = 1 and o.refundTime BETWEEN :startTime  AND :endTime) ");
                } else {
                    sqlPay.append("SELECT r.recipeId, r.patientName, r.MPIID, dep.NAME, r.organName, r.doctorName, r.SignDate as signDate, '支付成功' as payType, o.PayTime as payTime, o.refundTime as refundTime, d.useTotalDose as dose, IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price))*d.useTotalDose as ActualPrice ,d.saleDrugCode,d.drugName,d.drugSpec,d.producer,IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) as price,d.drugId");
                    sqlPay.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID LEFT JOIN cdr_drugsenterprise dep ON o.EnterpriseId = dep.Id ");
                    sqlPay.append(" WHERE r.GiveMode = 1 and ((o.payflag = 1 OR o.refundflag = 1) and o.paytime BETWEEN :startTime  AND :endTime ) ");
                    sqlRefund.append("SELECT r.recipeId, r.patientName, r.MPIID, dep.NAME, r.organName, r.doctorName, r.SignDate as signDate, '退款成功' as payType, o.PayTime as payTime, o.refundTime as refundTime, d.useTotalDose as dose, (0-IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)))*d.useTotalDose as ActualPrice ,d.saleDrugCode,d.drugName,d.drugSpec,d.producer,(0-IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price))) as price,d.drugId");
                    sqlRefund.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode  INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID LEFT JOIN cdr_drugsenterprise dep ON o.EnterpriseId = dep.Id");
                    sqlRefund.append(" WHERE r.GiveMode = 1 and (o.refundflag = 1 and o.refundTime BETWEEN :startTime  AND :endTime) ");
                }
                if (organId != null) {
                    sqlPay.append(" and r.clinicOrgan = :organId");
                    sqlRefund.append(" and r.clinicOrgan = :organId");
                } else if (organIds != null && organIds.size() > 0) {
                    sqlPay.append(" and r.clinicOrgan in (:organIds)");
                    sqlRefund.append(" and r.clinicOrgan in (:organIds)");
                }
                if (depId != null) {
                    sqlPay.append(" and o.EnterpriseId = :depId");
                    sqlRefund.append(" and o.EnterpriseId = :depId");
                }
                if (drugId != null) {
                    sqlPay.append(" and d.drugId = :drugId and d.status = 1");
                    sqlRefund.append(" and d.drugId = :drugId and d.status = 1");
                } else {
                    sqlPay.append(" and d.`status` = 1");
                    sqlRefund.append(" and d.`status` = 1");
                }
                if (recipeId != null) {
                    sqlPay.append(" and r.recipeId = :recipeId");
                    sqlRefund.append(" and r.recipeId = :recipeId");
                }
                if (payType != null) {
                    sqlPay.append(" and o.payFlag = :payType ");
                    sqlRefund.append(" and o.payFlag = :payType ");
                }
                //退款的处方单需要展示两条记录，所以要在取一次
                if (payType != null && payType == 1) sql.append("SELECT * from ( ").append(sqlPay).append(" ) a");
                else if (payType != null && payType == 3)
                    sql.append("SELECT * from ( ").append(sqlRefund).append(" ) a");
                else
                    sql.append("SELECT * from ( ").append(sqlPay).append(" UNION ALL ").append(sqlRefund).append(" ) a");
                if (orderColumn != null) {
                    sql.append(" order by " + orderColumn + " ");
                }
                if (orderType != null) {
                    sql.append(orderType);
                }
                Query q = ss.createSQLQuery(sql.toString());
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                if (organId != null) {
                    q.setParameter("organId", organId);
                } else if (organIds != null && organIds.size() > 0) {
                    q.setParameterList("organIds", organIds);
                }
                if (depId != null) {
                    q.setParameter("depId", depId);
                }
                if (drugId != null) {
                    q.setParameter("drugId", drugId);
                }
                if (recipeId != null) {
                    q.setParameter("recipeId", recipeId);
                }
                if (payType != null) {
                    q.setParameter("payType", payType);
                }
                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<Object[]> result = q.list();
                List<Map<String, Object>> backList = new ArrayList<>();
                if (CollectionUtils.isNotEmpty(result)) {
                    Map<String, Object> vo;
                    for (Object[] objs : result) {
                        vo = new HashMap<String, Object>();
                        vo.put("recipeId", objs[0] == null ? null : (Integer) objs[0]);
                        vo.put("patientName", objs[1] == null ? null : (String) objs[1]);
                        vo.put("mpiId", objs[2] == null ? null : (String) objs[2]);
                        vo.put("enterpriseName", objs[3] == null ? null : (String) objs[3]);
                        vo.put("organName", objs[4] == null ? null : (String) objs[4]);
                        vo.put("doctorName", objs[5] == null ? null : (String) objs[5]);
                        vo.put("signDate", objs[6] == null ? null : (Date) objs[6]);
                        vo.put("payType", objs[7] == null ? null : objs[7].toString());
                        vo.put("payTime", objs[8] == null ? null : (Date) objs[8]);
                        vo.put("refundTime", objs[9] == null ? null : (Date) objs[9]);
                        vo.put("useTotalDose", objs[10] == null ? null : Double.valueOf(objs[10] + ""));
                        vo.put("actualPrice", objs[11] == null ? null : Double.valueOf(objs[11] + ""));
                        if (drugId == null) {
                            vo.put("saleDrugCode", objs[12] == null ? null : (String) objs[12]);
                            vo.put("drugName", objs[13] == null ? null : (String) objs[13]);
                            vo.put("drugSpec", objs[14] == null ? null : (String) objs[14]);
                            vo.put("producer", objs[15] == null ? null : (String) objs[15]);
                            vo.put("price", objs[16] == null ? null : Double.valueOf(objs[16] + ""));
                            vo.put("drugId", objs[17] == null ? null : (Integer) objs[17]);
                        }
                        backList.add(vo);
                    }
                }
                setResult(backList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据日期获取电子处方药企配送订单明细（总计数据）
     *
     * @param startTime 开始时间
     * @param endTime   截止时间
     * @param organId   机构ID
     * @param depId     药企ID
     * @return
     */
    public Map<String, Object> queryrecipeOrderDetailedTotal(Date startTime, Date endTime, Integer organId, List<Integer> organIds, Integer depId, Integer drugId, Integer recipeId, Integer payType) {
        HibernateStatelessResultAction<Map<String, Object>> action = new AbstractHibernateStatelessResultAction<Map<String, Object>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder sql = new StringBuilder();
                StringBuilder sqlPay = new StringBuilder();
                StringBuilder sqlRefund = new StringBuilder();
                if (drugId != null) {
                    sqlPay.append("SELECT count(1) as count, sum(IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) * d.useTotalDose) as totalPrice ");
                    sqlPay.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID");
                    sqlPay.append(" WHERE r.GiveMode = 1 and ((o.payflag = 1 OR o.refundflag = 1) and o.paytime BETWEEN :startTime  AND :endTime ) ");
                    sqlRefund.append("SELECT count(1) as count, sum(IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) * (0-d.useTotalDose)) as totalPrice ");
                    sqlRefund.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID");
                    sqlRefund.append(" WHERE r.GiveMode = 1 and (o.refundflag = 1 and o.refundTime BETWEEN :startTime  AND :endTime) ");
                } else {
                    sqlPay.append("SELECT count(1) as count, sum(IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) * d.useTotalDose) as totalPrice ");
                    sqlPay.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID ");
                    sqlPay.append(" WHERE r.GiveMode = 1  and ((o.payflag = 1 OR o.refundflag = 1) and o.paytime BETWEEN :startTime  AND :endTime ) ");
                    sqlRefund.append("SELECT count(1) as count, sum(IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) * (0-d.useTotalDose)) as totalPrice ");
                    sqlRefund.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON r.OrderCode = o.OrderCode INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID ");
                    sqlRefund.append(" WHERE r.GiveMode = 1 and (o.refundflag = 1 and o.refundTime BETWEEN :startTime  AND :endTime) ");
                }
                if (organId != null) {
                    sqlPay.append(" and r.clinicOrgan = :organId");
                    sqlRefund.append(" and r.clinicOrgan = :organId");
                } else if (organIds != null && organIds.size() > 0) {
                    sqlPay.append(" and r.clinicOrgan in (:organIds)");
                    sqlRefund.append(" and r.clinicOrgan in (:organIds)");
                }
                if (depId != null) {
                    sqlPay.append(" and o.EnterpriseId = :depId");
                    sqlRefund.append(" and o.EnterpriseId = :depId");
                }
                if (drugId != null) {
                    sqlPay.append(" and d.drugId = :drugId and d.status = 1 ");
                    sqlRefund.append(" and d.drugId = :drugId and d.status = 1 ");
                } else {
                    sqlPay.append(" and d.`status` = 1 ");
                    sqlRefund.append(" and d.`status` = 1 ");
                }
                if (recipeId != null) {
                    sqlPay.append(" and r.recipeId = :recipeId");
                    sqlRefund.append(" and r.recipeId = :recipeId");
                }
                if (payType != null) {
                    sqlPay.append(" and o.payFlag = :payType ");
                    sqlRefund.append(" and o.payFlag = :payType ");
                }


                //退款的处方单需要展示两条记录，所以要在取一次
                sql.append("SELECT sum(count), sum(totalPrice) as totalPrice  from ( ").append(sqlPay).append(" UNION ALL ").append(sqlRefund).append(" ) b");
                Query q = ss.createSQLQuery(sql.toString());
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                if (organId != null) {
                    q.setParameter("organId", organId);
                } else if (organIds != null && organIds.size() > 0) {
                    q.setParameterList("organIds", organIds);
                }
                if (depId != null) {
                    q.setParameter("depId", depId);
                }
                if (drugId != null) {
                    q.setParameter("drugId", drugId);
                }
                if (recipeId != null) {
                    q.setParameter("recipeId", recipeId);
                }
                if (payType != null) {
                    q.setParameter("payType", payType);
                }
                List<Object[]> result = q.list();
                Map<String, Object> vo = new HashMap();
                if (CollectionUtils.isNotEmpty(result)) {
                    vo.put("totalNum", result.get(0)[0]);
                    vo.put("totalPrice", result.get(0)[1]);
                }
                setResult(vo);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }


    /**
     * 根据日期获取电子处方药企配送药品
     *
     * @param startTime 开始时间
     * @param endTime   截止时间
     * @param organId   机构ID
     * @param depId     药企ID
     * @return
     */
    public List<Map<String, Object>> queryrecipeDrug(Date startTime, Date endTime, Integer organId, List<Integer> organIds, Integer depId, Integer recipeId, String orderColumn, String orderType, int start, int limit) {
        HibernateStatelessResultAction<List<Map<String, Object>>> action = new AbstractHibernateStatelessResultAction<List<Map<String, Object>>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();

                if (recipeId != null) {
                    hql.append("SELECT d.saleDrugCode, d.drugName, d.producer, d.drugSpec, d.DrugUnit, IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) as price, sum(d.useTotalDose) as dose, sum(IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) * d.useTotalDose) as totalPrice, s.organId, s.DrugId ");
                } else {
                    hql.append("SELECT d.saleDrugCode, d.drugName, d.producer, d.drugSpec, d.DrugUnit, IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) as price, sum(d.useTotalDose) as dose, sum(if(o.refundFlag=1,0,IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price))) * d.useTotalDose) as totalPrice, s.organId, s.DrugId ");
                }
                hql.append(" FROM cdr_recipe r INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId INNER JOIN cdr_recipeorder o ON o.OrderCode = r.OrderCode ");
                hql.append("  LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID ");
                hql.append(" WHERE r.GiveMode = 1 and d.status = 1 and ((o.payflag = 1 and o.paytime BETWEEN :startTime  AND :endTime ) OR (o.refundflag = 1 and o.refundTime BETWEEN :startTime  AND :endTime)) ");
                if (organId != null) {
                    hql.append(" and r.clinicOrgan = :organId");
                } else if (organIds != null && organIds.size() > 0) {
                    hql.append(" and r.clinicOrgan in (:organIds)");
                }
                if (depId != null) {
                    hql.append(" and o.EnterpriseId = :depId");
                }
                if (recipeId != null) {
                    hql.append(" and r.recipeId = :recipeId");
                }
                hql.append(" GROUP BY s.drugId, s.OrganID");
                if (orderColumn != null) {
                    hql.append(" order by " + orderColumn + " ");
                }
                if (orderType != null) {
                    hql.append(orderType);
                }
                Query q = ss.createSQLQuery(hql.toString());
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                if (organId != null) {
                    q.setParameter("organId", organId);
                } else if (organIds != null && organIds.size() > 0) {
                    q.setParameterList("organIds", organIds);
                }
                if (depId != null) {
                    q.setParameter("depId", depId);
                }
                if (recipeId != null) {
                    q.setParameter("recipeId", recipeId);
                }

                q.setFirstResult(start);
                q.setMaxResults(limit);
                List<Object[]> result = q.list();
                List<Map<String, Object>> backList = new ArrayList<>();

                if (CollectionUtils.isNotEmpty(result)) {
                    Map<String, Object> vo;
                    for (Object[] objs : result) {
                        vo = new HashMap<String, Object>();
                        vo.put("drugCode", objs[0] == null ? null : (String) objs[0]);
                        vo.put("drugName", objs[1] == null ? null : (String) objs[1]);
                        vo.put("producer", objs[2] == null ? null : (String) objs[2]);
                        vo.put("drugSpec", objs[3] == null ? null : (String) objs[3]);
                        vo.put("drugUnit", objs[4] == null ? null : (String) objs[4]);
                        vo.put("price", objs[5] == null ? null : Double.valueOf(objs[5] + ""));
                        vo.put("dose", objs[6] == null ? null : objs[6].toString());
                        vo.put("totalPrice", objs[7] == null ? null : Double.valueOf(objs[7] + ""));
                        vo.put("enterpriseId", objs[8] == null ? null : objs[8].toString());
                        vo.put("DrugId", objs[9] == null ? null : objs[9].toString());
                        backList.add(vo);
                    }
                }
                setResult(backList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据日期获取电子处方药企配送药品
     *
     * @param startTime 开始时间
     * @param endTime   截止时间
     * @param organId   机构ID
     * @param depId     药企ID
     * @return
     */
    public Map<String, Object> queryrecipeDrugtotal(Date startTime, Date endTime, Integer organId, List<Integer> organIds, Integer depId, Integer recipeId) {
        HibernateStatelessResultAction<Map<String, Object>> action = new AbstractHibernateStatelessResultAction<Map<String, Object>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder();
                if (recipeId != null) {
                    hql.append("SELECT count(1), sum(totalPrice) from (SELECT sum(IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price)) * d.useTotalDose) as totalPrice ");
                } else {
                    hql.append("SELECT count(1), sum(totalPrice) from (SELECT sum(if(o.refundFlag=1,0,IF(d.settlementMode = 1,ifnull( d.his_return_sale_price, d.salePrice ),ifnull(d.actualSalePrice, s.price))) * d.useTotalDose) as totalPrice ");
                }
                hql.append(" FROM cdr_recipe r INNER JOIN cdr_recipedetail d ON r.recipeId = d.recipeId INNER JOIN cdr_recipeorder o ON o.OrderCode = r.OrderCode ");
                hql.append("  LEFT JOIN base_saledruglist s ON d.drugId = s.drugId and o.EnterpriseId = s.OrganID ");
                hql.append(" WHERE r.GiveMode = 1 and d.status = 1 and ((o.payflag = 1 and o.paytime BETWEEN :startTime  AND :endTime ) OR (o.refundflag = 1 and o.refundTime BETWEEN :startTime  AND :endTime)) ");
                if (organId != null) {
                    hql.append(" and r.clinicOrgan = :organId");
                } else if (organIds != null && organIds.size() > 0) {
                    hql.append(" and r.clinicOrgan in (:organIds)");
                }
                if (depId != null) {
                    hql.append(" and o.EnterpriseId = :depId");
                }
                if (recipeId != null) {
                    hql.append(" and r.recipeId = :recipeId");
                }
                hql.append(" GROUP BY s.drugId, s.OrganID) a");
                Query q = ss.createSQLQuery(hql.toString());
                q.setParameter("startTime", startTime);
                q.setParameter("endTime", endTime);
                if (organId != null) {
                    q.setParameter("organId", organId);
                } else if (organIds != null && organIds.size() > 0) {
                    q.setParameterList("organIds", organIds);
                }
                if (depId != null) {
                    q.setParameter("depId", depId);
                }
                if (recipeId != null) {
                    q.setParameter("recipeId", recipeId);
                }
                List<Object[]> result = q.list();

                Map<String, Object> vo = new HashMap();
                if (CollectionUtils.isNotEmpty(result)) {
                    vo.put("totalNum", result.get(0)[0]);
                    vo.put("totalPrice", result.get(0)[1]);
                }
                setResult(vo);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public List<BillBusFeeBean> findRecipeFeeList(final RecipeBillBean recipeBillRequest) {
        HibernateStatelessResultAction<List<BillBusFeeBean>> action = new AbstractHibernateStatelessResultAction<List<BillBusFeeBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer paySql = new StringBuffer();
                paySql.append("SELECT  r.ClinicOrgan, count(*), sum(IFNULL(o.ActualPrice, 0)) ");
                paySql.append(" FROM cdr_recipe r, cdr_recipeorder o ");
                paySql.append(" WHERE r.OrderCode = o.OrderCode AND o.ActualPrice != 0 ");
                paySql.append(" AND  PayTime >= :startTime AND PayTime < :endTime ");
                paySql.append(" GROUP BY r.ClinicOrgan ");
                Query paySqlQuery = ss.createSQLQuery(paySql.toString());
                paySqlQuery.setParameter("startTime", recipeBillRequest.getStartTime());
                paySqlQuery.setParameter("endTime", recipeBillRequest.getEndTime());
                paySqlQuery.setFirstResult(0);
                paySqlQuery.setMaxResults(0);
                List<Object[]> payList = paySqlQuery.list();

                StringBuffer refundSql = new StringBuffer();
                refundSql.append("SELECT r.ClinicOrgan, count(*), sum(IFNULL(o.ActualPrice, 0)) ");
                refundSql.append(" FROM cdr_recipe r, cdr_recipeorder o ");
                refundSql.append(" WHERE r.OrderCode = o.OrderCode AND o.refundFlag = 1 ");
                refundSql.append(" AND  refundTime >= :startTime AND refundTime < :endTime ");
                refundSql.append(" GROUP BY r.ClinicOrgan ");
                Query refundSqlQuery = ss.createSQLQuery(refundSql.toString());
                refundSqlQuery.setParameter("startTime", recipeBillRequest.getStartTime());
                refundSqlQuery.setParameter("endTime", recipeBillRequest.getEndTime());
                refundSqlQuery.setFirstResult(0);
                refundSqlQuery.setMaxResults(0);
                List<Object[]> refundList = refundSqlQuery.list();
                setResult(convertToBBFVList(recipeBillRequest.getAcctDate(), payList, refundList));
            }

            private List<BillBusFeeBean> convertToBBFVList(String acctDate, List<Object[]> payList, List<Object[]> refundList) {
                Set<Integer> organSet = fetchAllOrgan(payList, refundList);
                List<BillBusFeeBean> voList = Lists.newArrayList();
                for (Integer organId : organSet) {
                    BillBusFeeBean vo = newBillBusFeeVo(acctDate, organId);
                    fullFillPayPartIfExists(vo, payList);
                    fullFillRefundPartIfExists(vo, refundList);
                    vo.setAggregateAmount(vo.getPayAmount() - vo.getRefundAmount());
                    voList.add(vo);
                }
                return voList;
            }

            private void fullFillRefundPartIfExists(BillBusFeeBean vo, List<Object[]> refundList) {
                for (Object[] ros : refundList) {
                    Integer organId = ConversionUtils.convert(ros[0], Integer.class);
                    if (vo.getOrganId().equals(organId)) {
                        vo.setRefundCount(ConversionUtils.convert(ros[1], Integer.class));
                        vo.setRefundAmount(ConversionUtils.convert(ros[2], Double.class));
                        break;
                    }
                }
            }

            private void fullFillPayPartIfExists(BillBusFeeBean vo, List<Object[]> payList) {
                for (Object[] pos : payList) {
                    Integer organId = ConversionUtils.convert(pos[0], Integer.class);
                    if (vo.getOrganId().equals(organId)) {
                        vo.setPayCount(ConversionUtils.convert(pos[1], Integer.class));
                        vo.setPayAmount(ConversionUtils.convert(pos[2], Double.class));
                        break;
                    }
                }
            }

            private BillBusFeeBean newBillBusFeeVo(String acctDate, Integer organId) {
                BillBusFeeBean vo = new BillBusFeeBean();
                vo.setAcctMonth(acctDate.substring(0, 7));
                vo.setAcctDate(acctDate);
                vo.setFeeType(1);
                vo.setFeeTypeName("电子处方实收");
                vo.setOrganId(organId);
                vo.setPayCount(0);
                vo.setPayAmount(0.0);
                vo.setRefundCount(0);
                vo.setRefundAmount(0.0);
                vo.setCreateTime(new Date());
                vo.setUpdateTime(new Date());
                return vo;
            }

            private Set<Integer> fetchAllOrgan(List<Object[]> payList, List<Object[]> refundList) {
                Set<Integer> organSet = new HashSet<>();
                for (Object[] pos : payList) {
                    organSet.add(ConversionUtils.convert(pos[0], Integer.class));
                }
                for (Object[] ros : refundList) {
                    organSet.add(ConversionUtils.convert(ros[0], Integer.class));
                }
                return organSet;
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public List<BillDrugFeeBean> findDrugFeeList(final RecipeBillBean recipeBillRequest) {
        HibernateStatelessResultAction<List<BillDrugFeeBean>> action = new AbstractHibernateStatelessResultAction<List<BillDrugFeeBean>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuffer sql = new StringBuffer();
                sql.append("SELECT r.ClinicOrgan, r.enterpriseId, d.name, r.RecipeType, sum(o.RecipeFee) ");
                sql.append(" FROM cdr_recipe r INNER JOIN cdr_recipeorder o ON (r.OrderCode = o.OrderCode) LEFT JOIN cdr_drugsenterprise d ON (r.EnterpriseId = d.id) ");
                sql.append(" WHERE r.OrderCode = o.OrderCode AND o.Effective = 1 AND o.PayFlag=1 ");
                sql.append(" AND o.FinishTime>= :startTime AND o.FinishTime< :endTime AND o.status = 5 ");
                sql.append(" AND r.enterpriseId is not null ");
                sql.append(" GROUP BY r.ClinicOrgan, r.enterpriseId, d.name, r.RecipeType");
                Query sqlQuery = ss.createSQLQuery(sql.toString());
                sqlQuery.setParameter("startTime", recipeBillRequest.getStartTime());
                sqlQuery.setParameter("endTime", recipeBillRequest.getEndTime());
                sqlQuery.setFirstResult(0);
                sqlQuery.setMaxResults(0);
                List<Object[]> list = sqlQuery.list();
                setResult(convertToBDFVList(recipeBillRequest.getAcctDate(), list));
            }

            private List<BillDrugFeeBean> convertToBDFVList(String acctDate, List<Object[]> list) {
                List<BillDrugFeeBean> voList = Lists.newArrayList();
                for (Object[] objs : list) {
                    BillDrugFeeBean vo = new BillDrugFeeBean();
                    vo.setAcctMonth(acctDate.substring(0, 8));
                    vo.setAcctDate(acctDate);
                    vo.setOrganId(ConversionUtils.convert(objs[0], Integer.class));
                    vo.setDrugCompany(ConversionUtils.convert(objs[1], Integer.class));
                    vo.setDrugCompanyName(ConversionUtils.convert(objs[2], String.class));
                    vo.setDrugType(ConversionUtils.convert(objs[3], Integer.class));
                    vo.setDrugTypeName(DRUG_TYPE_TABLE.get(vo.getDrugType()));
                    vo.setAmount(ConversionUtils.convert(objs[4], Double.class));
                    vo.setCreateTime(new Date());
                    vo.setUpdateTime(new Date());
                    voList.add(vo);
                }
                return voList;
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @DAOMethod(sql = "from RecipeOrder where orderCode in (:recipeCodes) and effective = 1", limit = 0)
    public abstract List<RecipeOrder> findValidListbyCodes(@DAOParam("recipeCodes") List<String> recipeCodes);

    @DAOMethod(sql = "update RecipeOrder set dispensingApothecaryName=:dispensingApothecaryName,dispensingApothecaryIdCard=:dispensingApothecaryIdCard where orderCode=:orderCode")
    public abstract void updateApothecaryByOrderId(@DAOParam("orderCode") String orderCode,
                                                   @DAOParam("dispensingApothecaryName") String dispensingApothecaryName,
                                                   @DAOParam("dispensingApothecaryIdCard") String dispensingApothecaryIdCard);


    public List<RecivedDispatchedBalanceResponse> findDrugReceivedDispatchedBalanceList(final String manageUnit, final List<Integer> organIdList, final Date startTime, final Date endTime,
                                                                                        final Integer start, final Integer limit) {
        HibernateStatelessResultAction<List<RecivedDispatchedBalanceResponse>> action = new AbstractHibernateStatelessResultAction<List<RecivedDispatchedBalanceResponse>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder sql = new StringBuilder("SELECT OrganId,enterpriseName, lastBalance, thisRecived, thisDispatched, lastBalance+thisRecived-thisDispatched from(" +
                        "select d.Name enterpriseName,c.OrganId,sum(if(c.status !=5 and c.PayTime < :startTime,ActualPrice,0.00)) lastBalance,sum( if(c.PayTime between :startTime and :endTime , ActualPrice,0.00) ) thisRecived,sum(if(c.status =5 and c.finishTime between :startTime and :endTime  , ActualPrice,0.00)) thisDispatched ");
                StringBuilder searchSql = new StringBuilder(" from cdr_recipeorder c, cdr_drugsenterprise d where c.EnterpriseId = d.Id");
                if (CollectionUtils.isNotEmpty(organIdList)) {
                    searchSql.append(" and c.OrganId in :organIdList");
                }
                searchSql.append(" and c.payflag = 1 and c.Effective =1 GROUP BY c.EnterpriseId) t");
                Query query = ss.createSQLQuery(sql.append(searchSql).toString());
                query.setParameter("startTime", startTime);
                query.setParameter("endTime", endTime);

                if (CollectionUtils.isNotEmpty(organIdList)) {
                    query.setParameterList("organIdList", organIdList);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);

                List<Object[]> queryList = query.list();
                List<RecivedDispatchedBalanceResponse> resultList = Lists.newArrayList();
                if (CollectionUtils.isNotEmpty(queryList)) {
                    for (Object[] item : queryList) {
                        RecivedDispatchedBalanceResponse response = new RecivedDispatchedBalanceResponse();
                        response.setOrganId(ConversionUtils.convert(item[0], Integer.class));
                        response.setEnterpriseName(ConversionUtils.convert(item[1], String.class));
                        response.setLastBalance(ConversionUtils.convert(item[2], BigDecimal.class));
                        response.setThisRecived(ConversionUtils.convert(item[3], BigDecimal.class));
                        response.setThisDispatched(ConversionUtils.convert(item[4], BigDecimal.class));
                        response.setThisBalance(ConversionUtils.convert(item[5], BigDecimal.class));
                        resultList.add(response);
                    }
                }
                setResult(resultList);

            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public List<RecipeMonthAccountCheckResponse> findRecipeMonthAccountCheckList(final List<Integer> organIdList,
                                                                                 final Integer start, final Integer limit, final Date firstDate, Date endDate) {
        HibernateStatelessResultAction<List<RecipeMonthAccountCheckResponse>> action = new AbstractHibernateStatelessResultAction<List<RecipeMonthAccountCheckResponse>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder queryhql = new StringBuilder("SELECT er.ClinicOrgan ,er.OrganName ,count( * ),IFNULL( sum( ero.ActualPrice ), 0.00 )," +
                        "IFNULL( sum( ero.RecipeFee ),0.00 ) ,IFNULL( sum( ero.registerFee ), 0.00 )," +
                        "IFNULL( sum( ero.auditFee ), 0.00 )  ,IFNULL( sum( ero.expressFee ), 0.00 )," +
                        "sum( IF ( ero.payeeCode = 1, IFNULL( ero.ActualPrice, 0.00 ), 0.00 ) ) ,IFNULL( cast(sum( ero.fundAmount ) AS decimal(15,2)), 0.00 )," +
                        "sum( IF ( ero.payeeCode = 1, IFNULL( ero.ActualPrice, 0.00 ), 0.00 ) ) - IFNULL( cast(sum( ero.fundAmount ) AS decimal(15,2)), 0.00 )," +
                        "sum( IF ( ero.payeeCode = 0, IFNULL( ero.ActualPrice, 0.00 ), 0.00 ) ) ," +
                        "IFNULL( sum(ero.ActualPrice), 0.00 ) - IFNULL( sum(ero.auditFee), 0.00 ) - IFNULL( sum( ero.expressFee ), 0.00 ) -sum( IF ( ero.payeeCode = 1, IFNULL( ero.ActualPrice, 0.00 ), 0.00 ) )");
                StringBuilder sql = new StringBuilder(" FROM cdr_recipe er" +
                        " INNER JOIN cdr_recipeorder ero ON er.orderCode = ero.orderCode" +
                        " WHERE  ( ero.send_type = 1 or er.GiveMode = 2) and ero.payeeCode is not null");
                StringBuilder queryCount = new StringBuilder(" FROM cdr_recipe er" +
                        " INNER JOIN cdr_recipeorder ero ON er.orderCode = ero.orderCode " +
                        " WHERE  ( ero.send_type = 1 or er.GiveMode = 2) and ero.payeeCode is not null");

                if (CollectionUtils.isNotEmpty(organIdList)) {
                    sql.append(" And er.clinicOrgan IN :organIdList");
                }
                sql.append(" AND ero.PayTime >= :firstDate AND ero.PayTime <= :endDate GROUP BY er.ClinicOrgan ORDER BY er.ClinicOrgan");

                if (CollectionUtils.isNotEmpty(organIdList)) {
                    queryCount.append(" And er.clinicOrgan IN :organIdList");
                }

                queryCount.append(" AND ero.PayTime >= :firstDate AND ero.PayTime <= :endDate  GROUP BY er.ClinicOrgan ORDER BY er.ClinicOrgan) t");
                Query query = ss.createSQLQuery(queryhql.append(sql).toString());
                query.setParameter("firstDate", firstDate);
                query.setParameter("endDate", endDate);
                if (CollectionUtils.isNotEmpty(organIdList)) {
                    query.setParameterList("organIdList", organIdList);
                }
                query.setFirstResult(start);
                query.setMaxResults(limit);

                StringBuilder countSql = new StringBuilder("select count(*) from(select count(er.ClinicOrgan)");
                Query countQuery = ss.createSQLQuery(countSql.append(queryCount).toString());
                countQuery.setParameter("firstDate", firstDate);
                countQuery.setParameter("endDate", endDate);
                if (CollectionUtils.isNotEmpty(organIdList)) {
                    countQuery.setParameterList("organIdList", organIdList);
                }
                Long total = ConversionUtils.convert(countQuery.uniqueResult(), Long.class);

                List<Object[]> queryList = query.list();
                List<RecipeMonthAccountCheckResponse> resultList = new ArrayList<>(limit);
                if (CollectionUtils.isNotEmpty(queryList)) {
                    for (Object[] item : queryList) {
                        RecipeMonthAccountCheckResponse response = new RecipeMonthAccountCheckResponse();
                        response.setTotal(total);
                        response.setOrganId(ConversionUtils.convert(item[0], Integer.class));
                        response.setOrganName(ConversionUtils.convert(item[1], String.class));
                        response.setTotalOrderNum(ConversionUtils.convert(item[2], Integer.class));
                        response.setTotalFee(ConversionUtils.convert(item[3], BigDecimal.class));
                        response.setDrugFee(ConversionUtils.convert(item[4], BigDecimal.class));
                        response.setRegisterFee(ConversionUtils.convert(item[5], BigDecimal.class));
                        response.setCheckFee(ConversionUtils.convert(item[6], BigDecimal.class));
                        response.setDeliveryFee(ConversionUtils.convert(item[7], BigDecimal.class));
                        response.setOrganActualRecivedFee(ConversionUtils.convert(item[8], BigDecimal.class));
                        response.setMedicalInsurancePlanningFee(ConversionUtils.convert(item[9], BigDecimal.class));
                        response.setOrganAccountRecivedFee(ConversionUtils.convert(item[10], BigDecimal.class));
                        response.setNgariAccountRecivedFee(ConversionUtils.convert(item[11], BigDecimal.class));
                        response.setOrganRecivedDiffFee(ConversionUtils.convert(item[12], BigDecimal.class));
                        resultList.add(response);
                    }
                }
                setResult(resultList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public List<RecipeAccountCheckDetailResponse> findRecipeAccountCheckDetailList(final RecipeReportFormsRequest request) {
        HibernateStatelessResultAction<List<RecipeAccountCheckDetailResponse>> action = new AbstractHibernateStatelessResultAction<List<RecipeAccountCheckDetailResponse>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder queryhql = new StringBuilder("SELECT er.recipeId ,er.patientName ,er.MPIID ,ero.paytime ," +
                        "IFNULL(ero.ActualPrice ,0.00),IFNULL(ero.RecipeFee  ,0.00),IFNULL(ero.registerFee ,0.00) ," +
                        "IFNULL(ero.auditFee  ,0.00) ,IFNULL(ero.expressFee  ,0.00) ,IF ( ero.payeeCode = 1, " +
                        "IFNULL( ero.ActualPrice, 0.00 ), 0.00 ) ,IFNULL(ero.fundAmount  ,0.00) ," +
                        "IF ( ero.payeeCode = 1, IFNULL( ero.ActualPrice, 0.00 ), 0.00 ) - cast(IFNULL(ero.fundAmount  ,0.00) AS decimal(15,2)) ," +
                        "IF ( ero.payeeCode = 0, IFNULL( ero.ActualPrice, 0.00 ), 0.00 )   ," +
                        "IFNULL(ero.ActualPrice ,0.00) - IFNULL(ero.auditFee  ,0.00) - IFNULL(ero.expressFee  ,0.00) - IF ( ero.payeeCode = 1, IFNULL( ero.ActualPrice, 0.00 ), 0.00 ),ero.outTradeNo,er.recipeCode,ero.EnterpriseId,ero.refundFlag");
                StringBuilder sql = new StringBuilder(" FROM cdr_recipe er" +
                        " INNER JOIN cdr_recipeorder ero ON er.orderCode = ero.orderCode " +
                        " WHERE ( ero.send_type = 1 or er.GiveMode = 2) and ero.payeeCode is not null AND ero.paytime BETWEEN :startTime AND :endTime");
                if (StringUtils.isNotEmpty(request.getRecipeId())) {
                    sql.append(" And er.recipeId =:recipeId");
                }
                if (StringUtils.isNotEmpty(request.getMpiId())) {
                    sql.append(" And er.mpiid =:mpiId");
                }
                if (StringUtils.isNotEmpty(request.getTradeNo())) {
                    sql.append(" And ero.outTradeNo =:outTradeNo");
                }
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    sql.append(" And er.clinicOrgan in :organIdList");
                }
                if (null != request.getRefundFlag()) {
                    sql.append(" And ero.refundFlag = :refundFlag");
                }
                Query query = ss.createSQLQuery(queryhql.append(sql).toString());
                query.setFirstResult(request.getStart());
                query.setMaxResults(request.getLimit());
                query.setParameter("startTime", request.getStartTime());
                query.setParameter("endTime", request.getEndTime());
                if (StringUtils.isNotEmpty(request.getRecipeId())) {
                    query.setParameter("recipeId", request.getRecipeId());
                }
                if (StringUtils.isNotEmpty(request.getMpiId())) {
                    query.setParameter("mpiId", request.getMpiId());
                }
                if (StringUtils.isNotEmpty(request.getTradeNo())) {
                    query.setParameter("outTradeNo", request.getTradeNo());
                }
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    query.setParameterList("organIdList", request.getOrganIdList());
                }
                if (null != request.getRefundFlag()) {
                    query.setParameter("refundFlag", request.getRefundFlag());
                }

                StringBuilder countSql = new StringBuilder("SELECT count(*)");
                Query countQuery = ss.createSQLQuery(countSql.append(sql).toString());
                countQuery.setParameter("startTime", request.getStartTime());
                countQuery.setParameter("endTime", request.getEndTime());
                if (StringUtils.isNotEmpty(request.getRecipeId())) {
                    countQuery.setParameter("recipeId", request.getRecipeId());
                }
                if (StringUtils.isNotEmpty(request.getMpiId())) {
                    countQuery.setParameter("mpiId", request.getMpiId());
                }
                if (StringUtils.isNotEmpty(request.getTradeNo())) {
                    countQuery.setParameter("outTradeNo", request.getTradeNo());
                }
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    countQuery.setParameterList("organIdList", request.getOrganIdList());
                }
                if (null != request.getRefundFlag()) {
                    countQuery.setParameter("refundFlag", request.getRefundFlag());
                }

                Long total = ConversionUtils.convert(countQuery.uniqueResult(), Long.class);

                List<Object[]> queryList = query.list();
                List<RecipeAccountCheckDetailResponse> resultList = new ArrayList<>(request.getLimit());
                if (CollectionUtils.isNotEmpty(queryList)) {
                    for (Object[] item : queryList) {
                        RecipeAccountCheckDetailResponse response = new RecipeAccountCheckDetailResponse();
                        response.setTotal(total);
                        response.setRecipeId(ConversionUtils.convert(item[0], Integer.class));
                        response.setPatientName(ConversionUtils.convert(item[1], String.class));
                        response.setMpiId(ConversionUtils.convert(item[2], String.class));
                        response.setPayDate(ConversionUtils.convert(item[3], Date.class));
                        response.setTotalFee(ConversionUtils.convert(item[4], BigDecimal.class));
                        response.setDrugFee(ConversionUtils.convert(item[5], BigDecimal.class));
                        response.setRegisterFee(ConversionUtils.convert(item[6], BigDecimal.class));
                        response.setCheckFee(ConversionUtils.convert(item[7], BigDecimal.class));
                        response.setDeliveryFee(ConversionUtils.convert(item[8], BigDecimal.class));
                        response.setOrganActualRecivedFee(ConversionUtils.convert(item[9], BigDecimal.class));
                        response.setMedicalInsurancePlanningFee(ConversionUtils.convert(item[10], BigDecimal.class));
                        response.setOrganAccountRecivedFee(ConversionUtils.convert(item[11], BigDecimal.class));
                        response.setNgariAccountRecivedFee(ConversionUtils.convert(item[12], BigDecimal.class));
                        response.setOrganRecivedDiffFee(ConversionUtils.convert(item[13], BigDecimal.class));
                        response.setTradeNo(ConversionUtils.convert(item[14], String.class));
                        response.setRecipeCode(ConversionUtils.convert(item[15], String.class));
                        response.setEnterpriseId(ConversionUtils.convert(item[16], Integer.class));
                        response.setRefundFlag(ConversionUtils.convert(item[17], Integer.class));
                        resultList.add(response);
                    }
                }
                setResult(resultList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public List<EnterpriseRecipeMonthSummaryResponse> findEnterpriseRecipeMonthSummaryList(final RecipeReportFormsRequest request) {
        HibernateStatelessResultAction<List<EnterpriseRecipeMonthSummaryResponse>> action = new AbstractHibernateStatelessResultAction<List<EnterpriseRecipeMonthSummaryResponse>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder queryhql = new StringBuilder("SELECT c.OrganId,r.organName,c.EnterpriseId, COUNT(c.OrderId), SUM(c.ActualPrice), SUM(c.RecipeFee), IFNULL(SUM(IF(c.expressFeePayWay in (2,3),0,c.ExpressFee)),0), 0");
                StringBuilder sql = new StringBuilder(" from cdr_recipeorder c, cdr_recipe r" +
                        " where ( c.send_type = 2 OR r.GiveMode = 3 ) and  c.OrderCode = r.OrderCode  and c.payflag = 1 and c.Effective =1  and YEAR(c.PayTime) =:year and MONTH(c.PayTime) =:month");
                if (null != request.getEnterpriseId()) {
                    sql.append(" and c.EnterpriseId =:enterpriseId");
                }
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    sql.append(" and c.OrganId =:organIdList");
                }
                sql.append(" GROUP BY c.OrganId,c.EnterpriseId");
                StringBuilder querySql = queryhql.append(sql);
                Query query = ss.createSQLQuery(querySql.toString());
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    query.setParameterList("organIdList", request.getOrganIdList());
                }
                query.setFirstResult(request.getStart());
                query.setMaxResults(request.getLimit());
                query.setParameter("year", request.getYear());
                query.setParameter("month", request.getMonth());
                if (null != request.getEnterpriseId()) {
                    query.setParameter("enterpriseId", request.getEnterpriseId());
                }
                List<Object[]> queryList = query.list();

                StringBuilder countSql = new StringBuilder("select count(*) from(select count(c.EnterpriseId)");
                Query countQuery = ss.createSQLQuery(countSql.append(sql).append(")t").toString());
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    countQuery.setParameterList("organIdList", request.getOrganIdList());
                }
                countQuery.setParameter("year", request.getYear());
                countQuery.setParameter("month", request.getMonth());
                if (null != request.getEnterpriseId()) {
                    countQuery.setParameter("enterpriseId", request.getEnterpriseId());
                }
                Long total = ConversionUtils.convert(countQuery.uniqueResult(), Long.class);

                List<EnterpriseRecipeMonthSummaryResponse> resultList = new ArrayList<>(request.getLimit());
                if (CollectionUtils.isNotEmpty(queryList)) {
                    for (Object[] item : queryList) {
                        EnterpriseRecipeMonthSummaryResponse response = new EnterpriseRecipeMonthSummaryResponse();
                        response.setTotal(total);
                        response.setOrganId(ConversionUtils.convert(item[0], Integer.class));
                        response.setOrganName(ConversionUtils.convert(item[1], String.class));
                        response.setEnterpriseId(ConversionUtils.convert(item[2], Integer.class));
                        response.setTotalOrderNum(ConversionUtils.convert(item[3], Integer.class));
                        response.setTotalFee(ConversionUtils.convert(item[4], BigDecimal.class));
                        response.setDrugFee(ConversionUtils.convert(item[5], BigDecimal.class));
                        response.setDeliveryFee(ConversionUtils.convert(item[6], BigDecimal.class));
                        response.setNgariRecivedFee(ConversionUtils.convert(item[7], BigDecimal.class));
                        response.setOrganRecivedDiffFee(ConversionUtils.convert(item[6], BigDecimal.class));
                        resultList.add(response);
                    }
                }
                setResult(resultList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public List<EnterpriseRecipeDetailResponse> findEnterpriseRecipeDetailList(RecipeReportFormsRequest request) {
        HibernateStatelessResultAction<List<EnterpriseRecipeDetailResponse>> action = new AbstractHibernateStatelessResultAction<List<EnterpriseRecipeDetailResponse>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder countSql = new StringBuilder("SELECT count(*)");
                StringBuilder queryhql = new StringBuilder("SELECT c.OrganId,r.organName,c.EnterpriseId, r.RecipeID,c.MPIID, c.PayTime , IFNULL(c.ActualPrice,0) , IFNULL(c.RecipeFee,0), IF(c.expressFeePayWay in (2,3),0,c.ExpressFee), 0,c.outTradeNo,c.payeeCode ,r.GiveMode,r.RecipeCode");
                StringBuilder sql = new StringBuilder(" from cdr_recipeorder c, cdr_recipe r where ( c.send_type = 2 or r.GiveMode = 3) and c.OrderCode = r.OrderCode and c.payflag = 1 and c.Effective =1 and c.PayTime between :startTime and :endTime");
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    sql.append(" and c.OrganId in (:organIdList)");
                }
                if (null != request.getEnterpriseId()) {
                    sql.append(" and c.EnterpriseId =:enterpriseId");
                }
                if (null != request.getGiveMode()) {
                    sql.append(" and r.GiveMode =:giveMode");
                }
                if (null != request.getPayeeCode()) {
                    sql.append(" and c.payeeCode =:payeeCode");
                }
                StringBuilder querySql = queryhql.append(sql);
                Query query = ss.createSQLQuery(querySql.toString());
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    query.setParameterList("organIdList", request.getOrganIdList());
                }
                if (null != request.getEnterpriseId()) {
                    query.setParameter("enterpriseId", request.getEnterpriseId());
                }
                if (null != request.getGiveMode()) {
                    query.setParameter("giveMode", request.getGiveMode());
                }
                if (null != request.getPayeeCode()) {
                    query.setParameter("payeeCode", request.getPayeeCode());
                }
                query.setParameter("startTime", request.getStartTime());
                query.setParameter("endTime", request.getEndTime());
                query.setFirstResult(request.getStart());
                query.setMaxResults(request.getLimit());

                //count
                Query countQuery = ss.createSQLQuery(countSql.append(sql).toString());
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    countQuery.setParameterList("organIdList", request.getOrganIdList());
                }
                if (null != request.getEnterpriseId()) {
                    countQuery.setParameter("enterpriseId", request.getEnterpriseId());
                }
                if (null != request.getGiveMode()) {
                    countQuery.setParameter("giveMode", request.getGiveMode());
                }
                if (null != request.getPayeeCode()) {
                    countQuery.setParameter("payeeCode", request.getPayeeCode());
                }
                countQuery.setParameter("startTime", request.getStartTime());
                countQuery.setParameter("endTime", request.getEndTime());
                Long count = ConversionUtils.convert(countQuery.uniqueResult(), Long.class);

                List<Object[]> queryList = query.list();
                List<EnterpriseRecipeDetailResponse> resultList = new ArrayList<>(request.getLimit());
                if (CollectionUtils.isNotEmpty(queryList)) {
                    for (Object[] item : queryList) {
                        EnterpriseRecipeDetailResponse response = new EnterpriseRecipeDetailResponse();
                        response.setTotal(count);
                        response.setOrganId(ConversionUtils.convert(item[0], Integer.class));
                        response.setOrganName(ConversionUtils.convert(item[1], String.class));
                        response.setEnterpriseId(ConversionUtils.convert(item[2], Integer.class));
                        response.setRecipeId(ConversionUtils.convert(item[3], Integer.class));
                        response.setMpiId(ConversionUtils.convert(item[4], String.class));
                        response.setPayDate(ConversionUtils.convert(item[5], Date.class));
                        response.setTotalFee(ConversionUtils.convert(item[6], BigDecimal.class));
                        response.setDrugFee(ConversionUtils.convert(item[7], BigDecimal.class));
                        response.setDeliveryFee(ConversionUtils.convert(item[8], BigDecimal.class));
                        response.setNgariRecivedFee(ConversionUtils.convert(item[9], BigDecimal.class));
                        response.setEnterpriseReceivableFee(ConversionUtils.convert(item[8], BigDecimal.class));
                        response.setTradeNo(ConversionUtils.convert(item[10], String.class));
                        response.setPayeeCode(ConversionUtils.convert(item[11], Integer.class));
                        response.setGiveMode(ConversionUtils.convert(item[12], Integer.class));
                        response.setRecipeCode(ConversionUtils.convert(item[13], String.class));
                        resultList.add(response);
                    }
                }
                setResult(resultList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    //当前接口不需要进行分页
    public List<RecipeHisAccountCheckResponse> findRecipeHisAccountCheckList(final RecipeReportFormsRequest request) {
        HibernateStatelessResultAction<List<RecipeHisAccountCheckResponse>> action = new AbstractHibernateStatelessResultAction<List<RecipeHisAccountCheckResponse>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder countSql = new StringBuilder("SELECT count(*)");
                StringBuilder queryhql = new StringBuilder("SELECT cr.recipeId ,cr.patientName ,cr.MPIID ,( CASE cr.GiveMode WHEN 1 THEN \"配送到家\" WHEN 2 THEN \"医院取药\" WHEN 3 THEN \"药店取药\" WHEN 4 THEN \"患者自选\" else \"\" END )," +
                        "cr.GiveMode,cro.paytime,IFNULL(cro.ActualPrice,0.00),IFNULL(cro.fundAmount,0.00),IFNULL(cro.ActualPrice,0.00) - IFNULL(cro.fundAmount,0.00),cr.RecipeCode,cro.outTradeNo,cr.ClinicOrgan");
                StringBuilder sql = new StringBuilder(" FROM cdr_recipe cr" +
                        " INNER JOIN cdr_recipeorder cro ON cr.orderCode = cro.ordercode" +
                        " WHERE cro.paytime BETWEEN :startTime AND :endTime AND cro.outTradeNo is not null ");
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    sql.append(" AND cro.OrganId in :organIdList ");
                }
                if (null != request.getBuyMedicWay()) {
                    sql.append(" and cr.GiveMode =:giveMode");
                }
                StringBuilder querySql = queryhql.append(sql);
                Query query = ss.createSQLQuery(querySql.toString());
                if (CollectionUtils.isNotEmpty(request.getOrganIdList())) {
                    query.setParameterList("organIdList", request.getOrganIdList());
                }
                query.setParameter("startTime", request.getStartTime());
                query.setParameter("endTime", request.getEndTime());
                if (StringUtils.isNotEmpty(request.getBuyMedicWay())) {
                    query.setParameter("giveMode", request.getBuyMedicWay());
                }

                List<Object[]> queryList = query.list();
                List<RecipeHisAccountCheckResponse> resultList = new ArrayList<>();
                if (CollectionUtils.isNotEmpty(queryList)) {
                    for (Object[] item : queryList) {
                        RecipeHisAccountCheckResponse response = new RecipeHisAccountCheckResponse();
                        response.setRecipeId(ConversionUtils.convert(item[0], Integer.class));
                        response.setPatientName(ConversionUtils.convert(item[1], String.class));
                        response.setMpiId(ConversionUtils.convert(item[2], String.class));
                        response.setBuyMedicineWay(ConversionUtils.convert(item[3], String.class));
                        response.setGiverMode(ConversionUtils.convert(item[4], Integer.class));
                        response.setPayDate(ConversionUtils.convert(item[5], Date.class));
                        response.setTotalFee(ConversionUtils.convert(item[6], BigDecimal.class));
                        response.setMedicalInsurancePlanningFee(ConversionUtils.convert(item[7], BigDecimal.class));
                        response.setSelfPayFee(ConversionUtils.convert(item[8], BigDecimal.class));
                        response.setHisRecipeId(ConversionUtils.convert(item[9], String.class));
                        response.setTradeNo(ConversionUtils.convert(item[10], String.class));
                        response.setOrganId(ConversionUtils.convert(item[11], Integer.class));
                        //response.setTotal(count);
                        resultList.add(response);
                    }
                }
                setResult(resultList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public List<RecipeOrder> findRecipeOrderWitchLogistics() {
        HibernateStatelessResultAction<List<RecipeOrder>> action = new AbstractHibernateStatelessResultAction<List<RecipeOrder>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "from RecipeOrder  where LogisticsCompany IS NOT NULL AND TrackingNumber IS NOT NULL";

                Query q = ss.createQuery(sql);
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    /**
     * 根据患者id和机构id查询对应的未支付的订单
     *
     * @param mpiId
     * @param organId
     * @return
     */
    @DAOMethod(sql = "From RecipeOrder  where mpiId = :mpiId AND organId = :organId", limit = 0)
    public abstract List<RecipeOrder> queryRecipeOrderByMpiIdAndOrganId(@DAOParam("mpiId") String mpiId, @DAOParam("organId") Integer organId);

    @DAOMethod(sql = "From RecipeOrder  where mpiId = :mpiId AND organId in(:organIds)", limit = 0)
    public abstract List<RecipeOrder> queryRecipeOrderByMpiIdAndOrganIds(@DAOParam("mpiId") String mpiId, @DAOParam("organIds") List<Integer> organIds);


    /**
     * 根据订单号、物流单获取订单
     *
     * @param orderId          订单号
     * @param logisticsCompany 物流公司
     * @param trackingNumber   运单号
     * @return 订单
     */
    @DAOMethod(sql = "from RecipeOrder where orderId !=:orderId and logisticsCompany=:logisticsCompany and trackingNumber =:trackingNumber ")
    public abstract List<RecipeOrder> findByLogisticsCompanyAndTrackingNumber(@DAOParam("orderId") Integer orderId,
                                                                              @DAOParam("logisticsCompany") Integer logisticsCompany,
                                                                              @DAOParam("trackingNumber") String trackingNumber);

    public QueryResult<RecipeOrder> queryPageForCommonOrder(Date startDate, Date endDate, Integer start, Integer limit) {
        HibernateStatelessResultAction<QueryResult<RecipeOrder>> action = new AbstractHibernateStatelessResultAction<QueryResult<RecipeOrder>>() {
            @Override
            @SuppressWarnings("unchecked")
            public void execute(StatelessSession ss) throws DAOException {
                int total = 0;
                StringBuilder hql = new StringBuilder(" from RecipeOrder");
                if (startDate != null) {

                    hql.append(" where createTime >= :startTime ");
                }
                if (endDate != null) {
                    hql.append(" AND createTime <= :endTime ");
                }
                Query countQuery = ss.createQuery("select count(*) " + hql.toString());
                countQuery.setParameter("endTime", endDate);
                countQuery.setParameter("startTime", startDate);
                total = ((Long) countQuery.uniqueResult()).intValue();// 获取总条数

                hql.append(" order by orderId desc");
                Query query = ss.createQuery(hql.toString());
                query.setParameter("endTime", endDate);
                query.setParameter("startTime", startDate);
                query.setFirstResult(start);
                query.setMaxResults(limit);
                List<RecipeOrder> resList = query.list();
                QueryResult<RecipeOrder> qResult = new QueryResult<RecipeOrder>(total, query.getFirstResult(),
                        query.getMaxResults(), resList);
                setResult(qResult);
            }
        };
        HibernateSessionTemplate.instance().executeReadOnly(action);
        return (QueryResult<RecipeOrder>) action.getResult();
    }


    /**
     * 根据日期、机构查询订单支付和退款信息(只获取实际支付金额不为0的，调用支付平台的)
     *
     * @param billDate
     * @param organId
     * @param payOrganId
     * @return
     */
    public List<BusBillDateAccountDTO> findByPayTimeAndOrganIdAndPayOrganId(String billDate, Integer organId, String payOrganId) {
        HibernateStatelessResultAction<List<BusBillDateAccountDTO>> action = new AbstractHibernateStatelessResultAction<List<BusBillDateAccountDTO>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = findSqlForfindByPayTimeAndOrganIdAndPayOrganId(billDate, organId, payOrganId);
                Query q = ss.createSQLQuery(hql.toString());
                q.setParameter("time", billDate);
                if (organId != null) {
                    q.setParameter("organId", organId);
                }
                if (StringUtils.isNotEmpty(payOrganId)) {
                    q.setParameter("payOrganId", payOrganId);
                }

                List<Object[]> result = q.list();
                List<BusBillDateAccountDTO> backList = new ArrayList<>();
                if (CollectionUtils.isNotEmpty(result)) {
                    BusBillDateAccountDTO vo;
                    for (Object[] objs : result) {
                        vo = new BusBillDateAccountDTO();
                        //实际支付费用
                        Double actualPrice = (objs[15] == null ? null : Double.valueOf(objs[15] + ""));
                        //处方预结算返回支付总金额
                        Double preSettleTotalAmount = (objs[16] == null ? null : Double.valueOf(objs[16] + ""));
                        //处方预结算返回医保支付金额
                        Double fundAmount = (objs[17] == null ? null : Double.valueOf(objs[17] + ""));
                        //处方预结算返回自费金额
                        Double cashAmount = (objs[18] == null ? null : Double.valueOf(objs[18] + ""));

                        vo.setBusId(objs[0] == null ? null : (Integer) objs[0]);
                        vo.setOutTradeNo(objs[1] == null ? null : objs[1] + "");
                        vo.setTradeNo(objs[2] == null ? null : objs[2] + "");
                        vo.setTotalAmount(objs[15] == null ? null : (BigDecimal) objs[15]);
                        vo.setPaymentDate(objs[4] == null ? null : (Date) objs[4]);
                        vo.setSettlementNo(null);
                        vo.setTradeStatus(objs[11] == null ? null : objs[11] + "");
                        vo.setRefundAmount(objs[12] == null ? null : objs[12] + "");
                        //退费金额优先取preSettleTotalAmount 由于历史数据在走预结算的情况下actualprice和preSettleTotalAmount存在不一致【2021年11月大版本后会一致,preSettleTotalAmount金额回写到actualprice】
                        if ("2".equals(vo.getTradeStatus())) {
                            if (preSettleTotalAmount != null && preSettleTotalAmount != 0) {
                                vo.setRefundAmount(String.valueOf(preSettleTotalAmount));
                            }
                        }
                        vo.setRefundBatchNo(objs[13] == null ? null : objs[13] + "");
                        vo.setRefundDate(objs[14] == null ? null : (Date) objs[14]);
                        String wxPayWay = objs[10] == null ? null : objs[10] + "";
                        if (StringUtils.isNotEmpty(wxPayWay)) {
                            vo.setPayType(PayWayEnum.fromCode(wxPayWay).getPayType() + "");
                        }
                        vo.setBusType("6");
                        vo.setPatientId(objs[5] == null ? null : objs[5] + "");
                        vo.setPname(objs[6] == null ? null : objs[6] + "");
                        vo.setPhone(null);
                        vo.setMpiid(objs[7] == null ? null : objs[7] + "");

                        //医保、自费金额
                        if (preSettleTotalAmount == null || preSettleTotalAmount == 0) {
                            vo.setSettlementType("1");
                            vo.setPersonAmount(actualPrice == null ? null : BigDecimal.valueOf(actualPrice));
                        } else {
                            vo.setPersonAmount(cashAmount == null ? null : BigDecimal.valueOf(cashAmount));
                            vo.setTotalAmount(preSettleTotalAmount == null ? null : BigDecimal.valueOf(preSettleTotalAmount));
                            //如果医保金额为0或者null,则全自费  如果自费金额为0或者null,则全医保  否则部分医保部分自费
                            if (fundAmount == null || fundAmount == 0) {
                                vo.setSettlementType("1");
                            } else if (cashAmount == null || cashAmount == 0) {
                                vo.setSettlementType("2");
                            } else {
                                vo.setSettlementType("3");
                            }
                        }
                        vo.setOtherAmount(null);
                        vo.setRemark(null);
                        vo.setOrganId(objs[8] == null ? null : objs[8] + "");
                        vo.setPayOrganId(objs[9] == null ? null : objs[9] + "");
                        backList.add(vo);
                    }
                }
                setResult(backList);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public List<RecipeOrder> findOrderForEnterprise(List<Integer> enterpriseIdList, List<String> mpiIdList, Date beginTime, Date endTime) {
        HibernateStatelessResultAction<List<RecipeOrder>> action = new AbstractHibernateStatelessResultAction<List<RecipeOrder>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                StringBuilder hql = new StringBuilder("select a from RecipeOrder a,Recipe b where a.orderCode = b.orderCode and b.pushFlag = 0 and a.payFlag = 1 and a.effective = 1 and a.status in (2,3,12)" +
                        " and a.effective = 1 and a.enterpriseId in (:enterpriseIds) ");
                if (CollectionUtils.isNotEmpty(mpiIdList)) {
                    hql.append(" AND a.mpiId in (:mpiIdList) ");
                }
                if (null != beginTime) {
                    hql.append(" AND a.payTime >= :startTime ");
                }
                if (null != endTime) {
                    hql.append(" AND a.payTime <= :endTime ");
                }
                Query q = ss.createQuery(hql.toString());
                q.setParameterList("enterpriseIds", enterpriseIdList);
                if (CollectionUtils.isNotEmpty(mpiIdList)) {
                    q.setParameterList("mpiIdList", mpiIdList);
                }
                if (null != beginTime) {
                    q.setParameter("startTime", beginTime);
                }
                if (null != endTime) {
                    q.setParameter("endTime", endTime);
                }
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public QueryResult<RecipeOrder> findWaitApplyRefundRecipeOrder(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        final StringBuilder sbHql = this.generateWaitApplyRecipeHQL(recipeOrderRefundReqDTO);
        sbHql.append(" AND a.payFlag = 1 ");
        final StringBuilder sbHqlCount = this.generateWaitApplyRecipeHQLCount(recipeOrderRefundReqDTO);
        sbHqlCount.append(" AND a.payFlag = 1 ");
        HibernateStatelessResultAction<QueryResult<RecipeOrder>> action = new AbstractHibernateStatelessResultAction<QueryResult<RecipeOrder>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 查询总记录数
                SQLQuery sqlQuery = ss.createSQLQuery(sbHqlCount.toString());
                setRefundParameter(sqlQuery, recipeOrderRefundReqDTO);
                Long total = Long.valueOf(String.valueOf((sqlQuery.uniqueResult())));
                // 查询结果
                Query query = ss.createSQLQuery(sbHql.append(" order by a.CreateTime DESC").toString()).addEntity(RecipeOrder.class);
                logger.info("RecipeOderDAO findWaitApplyRefundRecipeOrder sbHql={}",sbHql);
                setRefundParameter(query, recipeOrderRefundReqDTO);
                query.setFirstResult(recipeOrderRefundReqDTO.getStart());
                query.setMaxResults(recipeOrderRefundReqDTO.getLimit());
                List<RecipeOrder> recipeOrderList = query.list();
                setResult(new QueryResult<>(total, query.getFirstResult(), query.getMaxResults(), recipeOrderList));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public QueryResult<RecipeOrder> findPushFailRecipeOrder(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        final StringBuilder sbHql = this.generatePushFailRecipeHQL(recipeOrderRefundReqDTO);
        final StringBuilder sbHqlCount = this.generatePushFailRecipeHQLCount(recipeOrderRefundReqDTO);
        HibernateStatelessResultAction<QueryResult<RecipeOrder>> action = new AbstractHibernateStatelessResultAction<QueryResult<RecipeOrder>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 查询总记录数
                SQLQuery sqlQuery = ss.createSQLQuery(sbHqlCount.toString());
                setRefundParameter(sqlQuery, recipeOrderRefundReqDTO);
                Long total = Long.valueOf(String.valueOf((sqlQuery.uniqueResult())));
                // 查询结果
                Query query = ss.createSQLQuery(sbHql.append(" order by a.CreateTime DESC").toString()).addEntity(RecipeOrder.class);
                logger.info("RecipeOderDAO findPushFailRecipeOrder sbHql={}",sbHql);
                setRefundParameter(query, recipeOrderRefundReqDTO);
                query.setFirstResult(recipeOrderRefundReqDTO.getStart());
                query.setMaxResults(recipeOrderRefundReqDTO.getLimit());
                List<RecipeOrder> recipeOrderList = query.list();
                setResult(new QueryResult<>(total, query.getFirstResult(), query.getMaxResults(), recipeOrderList));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    public QueryResult<RecipeOrder> findRefundRecipeOrder(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        final StringBuilder sbHql = this.generateRecipeHQL(recipeOrderRefundReqDTO);
        final StringBuilder sbHqlCount = this.generateRecipeHQLCount(recipeOrderRefundReqDTO);
        HibernateStatelessResultAction<QueryResult<RecipeOrder>> action = new AbstractHibernateStatelessResultAction<QueryResult<RecipeOrder>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                // 查询总记录数
                SQLQuery sqlQuery = ss.createSQLQuery(sbHqlCount.toString());
                setRefundParameter(sqlQuery, recipeOrderRefundReqDTO);
                Long total = Long.valueOf(String.valueOf((sqlQuery.uniqueResult())));
                // 查询结果
                Query query = ss.createSQLQuery(sbHql.append(" order by a.CreateTime DESC").toString()).addEntity(RecipeOrder.class);
                logger.info("RecipeOderDAO findRefundRecipeOrder sbHql={}", sbHql);
                setRefundParameter(query, recipeOrderRefundReqDTO);
                query.setFirstResult(recipeOrderRefundReqDTO.getStart());
                query.setMaxResults(recipeOrderRefundReqDTO.getLimit());
                List<RecipeOrder> recipeOrderList = query.list();
                setResult(new QueryResult<>(total, query.getFirstResult(), query.getMaxResults(), recipeOrderList));
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    private void setRefundParameter(Query query, RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        String orderCode = recipeOrderRefundReqDTO.getOrderCode();
        String patientName = recipeOrderRefundReqDTO.getPatientName();
        Integer organId = recipeOrderRefundReqDTO.getOrganId();
        Integer payFlag = recipeOrderRefundReqDTO.getPayFlag();
        Integer depId = recipeOrderRefundReqDTO.getDepId();
        List<Integer> organIds = recipeOrderRefundReqDTO.getOrganIds();

        if (StringUtils.isNotEmpty(orderCode)) {
            query.setParameter("orderCode", orderCode);
        }
        if (StringUtils.isNotEmpty(patientName)) {
            query.setParameter("patientName", "%" + patientName + "%");
        }
        if (CollectionUtils.isNotEmpty(organIds)) {
            query.setParameterList("organIds", organIds);
        }
        if (CollectionUtils.isEmpty(organIds) && null != organId) {
            query.setParameter("organId", organId);
        }
        if (null != payFlag) {
            query.setParameter("payFlag", payFlag);
        }
        if (null != recipeOrderRefundReqDTO.getBeginTime()) {
            query.setParameter("startTime", recipeOrderRefundReqDTO.getBeginTime());
        }
        if (null != recipeOrderRefundReqDTO.getEndTime()) {
            query.setParameter("endTime", recipeOrderRefundReqDTO.getEndTime());
        }
        if (null != recipeOrderRefundReqDTO.getDepId()) {
            query.setParameter("depId", depId);
        }
    }

    private StringBuilder generateWaitApplyRecipeHQL(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        StringBuilder hql = new StringBuilder("select DISTINCT(a.OrderCode), a.* from cdr_recipeorder a,cdr_recipe b,cdr_recipe_ext c where a.orderCode = b.orderCode AND b.recipeId = c.recipeId AND c.refundNodeStatus = 0 AND a.payFlag != 0  ");
        return getRefundStringBuilder(recipeOrderRefundReqDTO, hql);
    }

    private StringBuilder generateWaitApplyRecipeHQLCount(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        StringBuilder hql = new StringBuilder("select count(DISTINCT(a.OrderCode)) from cdr_recipeorder a,cdr_recipe b,cdr_recipe_ext c where a.orderCode = b.orderCode AND b.recipeId = c.recipeId AND c.refundNodeStatus = 0 AND a.payFlag != 0 ");
        return getRefundStringBuilder(recipeOrderRefundReqDTO, hql);
    }

    protected StringBuilder generateRecipeHQL(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        StringBuilder hql = new StringBuilder("select DISTINCT(a.OrderCode), a.* from cdr_recipeorder a,cdr_recipe b,cdr_recipe_ext c where a.orderCode = b.orderCode AND b.recipeId = c.recipeId ");
        return getRefundStringBuilder(recipeOrderRefundReqDTO, hql);
    }

    protected StringBuilder generateRecipeHQLCount(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        StringBuilder hql = new StringBuilder("select count(DISTINCT(a.OrderCode)) from cdr_recipeorder a,cdr_recipe b,cdr_recipe_ext c where a.orderCode = b.orderCode AND b.recipeId = c.recipeId ");
        return getRefundStringBuilder(recipeOrderRefundReqDTO, hql);
    }

    private StringBuilder generatePushFailRecipeHQL(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        StringBuilder hql = new StringBuilder("select DISTINCT(a.OrderCode), a.* from cdr_recipeorder a,cdr_recipe b,cdr_recipe_ext c where a.orderCode = b.orderCode AND b.recipeId = c.recipeId ");
        getRefundStringBuilder(recipeOrderRefundReqDTO, hql);
        hql.append(" AND a.pushFlag = -1 and a.payFlag = 1 AND b.giveMode in (1,3) ");
        return hql;
    }

    private StringBuilder generatePushFailRecipeHQLCount(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        StringBuilder hql = new StringBuilder("select count(DISTINCT(a.OrderCode)) from cdr_recipeorder a,cdr_recipe b,cdr_recipe_ext c where a.orderCode = b.orderCode AND b.recipeId = c.recipeId ");
        getRefundStringBuilder(recipeOrderRefundReqDTO, hql);
        hql.append(" AND a.pushFlag = -1 and a.payFlag = 1 AND b.giveMode in (1,3) ");
        return hql;
    }

    private StringBuilder getRefundStringBuilder(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO, StringBuilder hql) {
        //默认查询所有
        if (CollectionUtils.isNotEmpty(recipeOrderRefundReqDTO.getOrganIds())) {
            // 添加申请机构条件
            hql.append(" AND a.organId in (:organIds) ");
        }
        if (StringUtils.isNotEmpty(recipeOrderRefundReqDTO.getOrderCode())) {
            hql.append(" AND a.orderCode =:orderCode ");
        }
        if (StringUtils.isNotEmpty(recipeOrderRefundReqDTO.getPatientName())) {
            hql.append(" AND b.patientName like :patientName ");
        }
        if (CollectionUtils.isEmpty(recipeOrderRefundReqDTO.getOrganIds())) {
            if (null != recipeOrderRefundReqDTO.getOrganId()) {
                hql.append(" AND a.organId =:organId ");
            }
        }

        if (null != recipeOrderRefundReqDTO.getOrderStatus()) {
            if (RecipeOrderStatusEnum.ORDER_STATUS_READY_PAY.getType().equals(recipeOrderRefundReqDTO.getOrderStatus())) {
                hql.append(" AND a.status =1 ");
            } else if (new Integer(2).equals(recipeOrderRefundReqDTO.getOrderStatus())) {
                hql.append(" AND a.status in (2,3,4,12) ");
            } else {
                hql.append(" AND a.status = 5 ");
            }

        }
        if (null != recipeOrderRefundReqDTO.getRefundStatus()) {
            if (new Integer(0).equals(recipeOrderRefundReqDTO.getRefundStatus())) {
                hql.append(" AND (c.refundNodeStatus is null || c.refundNodeStatus = 2 || c.refundNodeStatus = 3) ");
            } else if (new Integer(1).equals(recipeOrderRefundReqDTO.getRefundStatus())) {
                hql.append(" AND c.refundNodeStatus in(0,4) ");
            } else {
                hql.append(" AND c.refundNodeStatus = 1 ");
            }
        }
        if (null != recipeOrderRefundReqDTO.getPayFlag()) {
            hql.append(" AND a.payFlag =:payFlag ");
        }
        if (null != recipeOrderRefundReqDTO.getBeginTime()) {
            hql.append(" AND a.createTime >= :startTime ");
        }
        if (null != recipeOrderRefundReqDTO.getEndTime()) {
            hql.append(" AND a.createTime <= :endTime ");
        }
        if (null != recipeOrderRefundReqDTO.getDepId()) {
            hql.append(" AND a.enterpriseId =:depId ");
        }
        if (null != recipeOrderRefundReqDTO.getInvoiceStatus()) {
            if (new Integer(1).equals(recipeOrderRefundReqDTO.getInvoiceStatus())) {
                //开具发票
                hql.append(" AND a.invoice_record_id is not null ");
            } else {
                //无需开具
                hql.append(" AND a.invoice_record_id is null ");
            }
        }

        if (null != recipeOrderRefundReqDTO.getFastRecipeFlag()) {
            if(Integer.valueOf(1).equals(recipeOrderRefundReqDTO.getFastRecipeFlag())){
                hql.append(" AND b.fast_recipe_flag = 1 ");
            }else{
                hql.append(" AND (b.fast_recipe_flag = 0 or b.fast_recipe_flag is null) ");
            }
        }
        logger.info("RecipeOrderDAO getRefundStringBuilder hql:{}", hql);
        return hql;
    }

    private StringBuilder findSqlForfindByPayTimeAndOrganIdAndPayOrganId(String billDate, Integer organId, String payOrganId) {
        StringBuilder hql = new StringBuilder();
        hql.append("select distinct a.* from ( ");
        hql.append(" select o.orderId, o.OutTradeNo,o.tradeNo,o.totalfee,o.payTime");
        hql.append(" ,r.patientID,r.patientName,r.mpiid,r.clinicorgan, o.payOrganId");
        hql.append(" ,o.wxPayWay , 1 tradeStatus,0 refundAmount,'' refundBatchNo,null refundDate ");
        hql.append(" ,o.actualPrice,o.preSettleTotalAmount,o.fundAmount,o.cashAmount");
        hql.append(" from cdr_recipe r INNER JOIN cdr_recipeorder o on r.OrderCode = o.OrderCode ");
        hql.append(" where o.payFlag = 1 and  to_days(o.payTime) = to_days(:time) and o.Effective = 1 and o.actualPrice <> 0 ");
        if (organId != null) {
            hql.append(" and  r.clinicOrgan =:organId");
        }
        if (StringUtils.isNotEmpty(payOrganId)) {
            hql.append(" and  o.payOrganId =:payOrganId");
        }
        hql.append(" UNION ALL ");
        hql.append(" select o.orderId, o.OutTradeNo,o.tradeNo,o.totalfee,o.payTime");
        hql.append(" ,r.patientID,r.patientName,r.mpiid,r.clinicorgan, o.payOrganId");
        hql.append(" ,o.wxPayWay , 2 tradeStatus,o.actualPrice refundAmount,o.OutTradeNo refundBatchNo, o.refundTime refundDate ");
        hql.append(" ,o.actualPrice,o.preSettleTotalAmount,o.fundAmount,o.cashAmount");
        hql.append(" from cdr_recipe r INNER JOIN cdr_recipeorder o on r.OrderCode = o.OrderCode ");
        hql.append(" where o.refundFlag is Not Null and o.refundFlag <> 0 and  to_days(o.refundTime) = to_days(:time) and o.actualPrice <> 0 ");
        if (organId != null) {
            hql.append(" and  r.clinicOrgan =:organId");
        }
        if (StringUtils.isNotEmpty(payOrganId)) {
            hql.append(" and  o.payOrganId =:payOrganId");
        }
        //退费当天的正向交易
        hql.append(" UNION ALL ");
        hql.append(" select o.orderId, o.OutTradeNo,o.tradeNo,o.totalfee,o.payTime");
        hql.append(" ,r.patientID,r.patientName,r.mpiid,r.clinicorgan, o.payOrganId");
        hql.append(" ,o.wxPayWay , 1 tradeStatus,0 refundAmount,'' refundBatchNo,null refundDate ");
        hql.append(" ,o.actualPrice,o.preSettleTotalAmount,o.fundAmount,o.cashAmount");
        hql.append(" from cdr_recipe r INNER JOIN cdr_recipeorder o on r.OrderCode = o.OrderCode ");
        hql.append(" where  o.refundFlag is Not Null and   o.refundFlag <> 0 and to_days(o.payTime) = to_days(:time)  and o.actualPrice <> 0 ");
        if (organId != null) {
            hql.append(" and  r.clinicOrgan =:organId");
        }
        if (StringUtils.isNotEmpty(payOrganId)) {
            hql.append(" and  o.payOrganId =:payOrganId");
        }
        hql.append(" ) a order by  a.payTime");
        return hql;
    }

    public List<RecipeOrder> findUnPushOrder(String startDate, String endDate) {
        HibernateStatelessResultAction<List<RecipeOrder>> action = new AbstractHibernateStatelessResultAction<List<RecipeOrder>>() {
            @Override
            public void execute(StatelessSession ss) throws Exception {
                String sql = "from RecipeOrder where createTime between '" + startDate + "' and '" + endDate + "' and pushFlag=-1 and effective=1";

                Query q = ss.createQuery(sql);
                setResult(q.list());
            }
        };

        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();
    }

    @DAOMethod(sql = "from RecipeOrder where mpiId =:mpiId and createTime >:date ")
    public abstract List<RecipeOrder> findByMpiIdAndDate(@DAOParam("mpiId")String mpiId, @DAOParam("date")Date date);

    @DAOMethod(sql = "update RecipeOrder set trackingNumber=:trackingNumber,logisticsCompany=:logisticsCompany where orderId=:orderId")
    public abstract void updateTrackingNumberByOrderId(@DAOParam("orderId")Integer orderId, @DAOParam("logisticsCompany")Integer logisticsCompany, @DAOParam("trackingNumber")String trackingNumber);

    public List<RecipeOrderDetailExportDTO> getRecipeOrderDetail(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO) {
        final StringBuilder orderCodeHQL = generateOrderCodeHQL(recipeOrderRefundReqDTO);
        final StringBuilder sbHql = this.generateRecipeOrderDetailHQL(recipeOrderRefundReqDTO);
        logger.info("RecipeOderDAO getRecipeOrderDetail sbHql = {} ", sbHql.toString());
        HibernateStatelessResultAction<List<RecipeOrderDetailExportDTO>> action = new AbstractHibernateStatelessResultAction<List<RecipeOrderDetailExportDTO>>() {
            @Override
            public void execute(StatelessSession ss) {
                Query orderCodeQuery = ss.createSQLQuery(orderCodeHQL.toString());
                setRefundParameter(orderCodeQuery, recipeOrderRefundReqDTO);
                List<String> orderCodeList = orderCodeQuery.list();

                Query query = ss.createSQLQuery(sbHql.toString()).addEntity(RecipeOrderDetailExportDTO.class);
                if(CollectionUtils.isNotEmpty(orderCodeList)){
                    query.setParameterList("orderCodeList",orderCodeList);
                }
                setRefundParameter(query, recipeOrderRefundReqDTO);
                List<RecipeOrderDetailExportDTO> list = query.list();
                setResult(list);
            }
        };
        HibernateSessionTemplate.instance().execute(action);
        return action.getResult();

    }
    private StringBuilder generateOrderCodeHQL(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO){
        StringBuilder hql = new StringBuilder("select ");
        hql.append("DISTINCT(b.orderCode) ");
        hql.append("from cdr_recipe b LEFT JOIN cdr_recipeorder a on b.orderCode=a.orderCode ");
        hql.append("LEFT JOIN cdr_recipedetail d ON b.RecipeID = d.RecipeID LEFT JOIN cdr_recipe_ext c on c.recipeId = b.recipeId ");
        hql.append(" where d.status= 1 ");
        getRefundStringBuilder(recipeOrderRefundReqDTO, hql);
        hql.append(" order by a.CreateTime DESC");
        if(null != recipeOrderRefundReqDTO.getStart()){
            hql.append(" limit ").append(recipeOrderRefundReqDTO.getStart());
        }
        if(null != recipeOrderRefundReqDTO.getLimit()){
            hql.append(" , ").append(recipeOrderRefundReqDTO.getLimit());
        }
        return hql;
    }

    protected StringBuilder generateRecipeOrderDetailHQL(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO){
        StringBuilder hql = new StringBuilder("select ");
        hql.append("b.recipeId,case when b.recipeType = 1 then '西药' when b.recipeType = 2 then '中成药' when b.recipeType = 3 then '中药' when b.recipeType = 4 then '膏方' else '其他' end as recipeType,");
        hql.append("b.recipeCode,b.appoint_depart_name as appointDepartName,b.doctorName,b.patientName,b.patientName as requestPatientName,b.CreateDate,b.requestMpiId,case when b.fast_recipe_flag = 1 then '快捷购药订单' else '普通订单' end as fastRecipeFlag, ");
        hql.append("a.OrderCode,a.process_state as processState,");
        hql.append("case when a.orderType in (1,2,3,4) then '医保' else '自费' end as orderTypeText,a.fundAmount,a.cashAmount,");
        hql.append("c.refundNodeStatus as refundNodeStatus,");
        hql.append("a.giveModeText,a.DrugStoreName,a.CreateTime as orderTime,a.PayTime,a.TotalFee,a.RecipeFee,a.ExpressFee,a.DecoctionFee,a.TCMFee,a.RegisterFee,a.AuditFee,a.TradeNo,a.RecMobile as mobile,");
        hql.append("c.decoctionText,");
        hql.append("cd.Name,if (b.recipeType = 3,case when a.patient_is_decoction = 0 then '否' when a.patient_is_decoction = 1 then '是' end,null) as generationisOfDecoction ");
        return generateRecipeOrderDetailHQLStatistics(recipeOrderRefundReqDTO,hql);
    }

    private StringBuilder generateRecipeOrderDetailHQLStatistics(RecipeOrderRefundReqDTO recipeOrderRefundReqDTO,StringBuilder hql){
        hql.append("from cdr_recipe b LEFT JOIN cdr_recipeorder a on b.orderCode=a.orderCode ");
        hql.append("LEFT JOIN cdr_recipedetail d ON b.RecipeID = d.RecipeID LEFT JOIN cdr_recipe_ext c on c.recipeId = b.recipeId ");
        hql.append("LEFT JOIN cdr_drugsenterprise cd ON cd.id = a.EnterpriseId ");
        hql.append("LEFT JOIN base_saledruglist bs ON bs.OrganID = a.EnterpriseId and bs.DrugId = d.DrugID ");
        hql.append(" where d.status= 1 and b.orderCode in (:orderCodeList) ");
        getRefundStringBuilder(recipeOrderRefundReqDTO, hql);
        hql.append(" order by a.CreateTime DESC");
        return hql;
    }
}
