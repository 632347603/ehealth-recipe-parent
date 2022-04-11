package recipe.business;

import com.alibaba.fastjson.JSON;
import com.ngari.recipe.dto.RecipeOrderRefundReqDTO;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import com.ngari.recipe.recipe.model.RecipeExtendBean;
import com.ngari.recipe.recipeorder.model.RecipeOrderBean;
import ctd.persistence.bean.QueryResult;
import ctd.util.JSONUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.core.api.greenroom.IRecipeOrderRefundService;
import recipe.dao.*;
import recipe.enumerate.status.PayModeEnum;
import recipe.manager.OrderManager;
import recipe.util.DateConversion;
import recipe.util.ObjectCopyUtils;
import recipe.vo.greenroom.RecipeOrderRefundDetailVO;
import recipe.vo.greenroom.RecipeOrderRefundPageVO;
import recipe.vo.greenroom.RecipeOrderRefundReqVO;
import recipe.vo.greenroom.RecipeOrderRefundVO;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 退费查询接口调用
 *
 * @author ys
 */
@Service
public class RecipeOrderRefundService implements IRecipeOrderRefundService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private RecipeOrderDAO recipeOrderDAO;
    @Autowired
    private RecipeDAO recipeDAO;
    @Autowired
    private RecipeExtendDAO recipeExtendDAO;
    @Autowired
    private DrugsEnterpriseDAO drugsEnterpriseDAO;
    @Autowired
    private OrderManager orderManager;
    @Autowired
    private RecipeDetailDAO recipeDetailDAO;

    @Override
    public RecipeOrderRefundPageVO findRefundRecipeOrder(RecipeOrderRefundReqVO recipeOrderRefundReqVO) {
        RecipeOrderRefundPageVO recipeOrderRefundPageVO = new RecipeOrderRefundPageVO();
        Date beginDate = DateConversion.parseDate(recipeOrderRefundReqVO.getBeginTime(), DateConversion.DEFAULT_DATE_TIME);
        Date endDate = DateConversion.parseDate(recipeOrderRefundReqVO.getEndTime(), DateConversion.DEFAULT_DATE_TIME);
        RecipeOrderRefundReqDTO recipeOrderRefundReqDTO = ObjectCopyUtils.convert(recipeOrderRefundReqVO, RecipeOrderRefundReqDTO.class);
        recipeOrderRefundReqDTO.setBeginTime(beginDate);
        recipeOrderRefundReqDTO.setEndTime(endDate);
        QueryResult<RecipeOrder> recipeOrderQueryResult = orderManager.findRefundRecipeOrder(recipeOrderRefundReqDTO);
        logger.info("RecipeOrderRefundService findRefundRecipeOrder recipeOrderQueryResult:{}", JSON.toJSONString(recipeOrderQueryResult));
        if (CollectionUtils.isEmpty(recipeOrderQueryResult.getItems())) {
            return recipeOrderRefundPageVO;
        }
        List<RecipeOrder> recipeOrderList = recipeOrderQueryResult.getItems();
        long total = recipeOrderQueryResult.getTotal();
        if (null != new Long(total)) {
            recipeOrderRefundPageVO.setTotal(new Long(total).intValue());
        }
        List<String> orderCodeList = recipeOrderList.stream().map(RecipeOrder::getOrderCode).collect(Collectors.toList());
        List<Integer> depIdList = recipeOrderList.stream().map(RecipeOrder::getEnterpriseId).collect(Collectors.toList());
        List<Recipe> recipeList = recipeDAO.findByOrderCode(orderCodeList);
        Map<String, Recipe> recipeOrderCodeMap = recipeList.stream().collect(Collectors.toMap(Recipe::getOrderCode,a->a,(k1,k2)->k1));
        List<Integer> recipeIdList = recipeList.stream().map(Recipe::getRecipeId).collect(Collectors.toList());
        List<RecipeExtend> recipeExtendList = recipeExtendDAO.queryRecipeExtendByRecipeIds(recipeIdList);
        Map<Integer, RecipeExtend> recipeExtendMap = recipeExtendList.stream().collect(Collectors.toMap(RecipeExtend::getRecipeId, a->a,(k1, k2)->k1));
        List<DrugsEnterprise> drugsEnterpriseList = drugsEnterpriseDAO.findByIdIn(depIdList);
        Map<Integer, DrugsEnterprise> drugsEnterpriseMap = drugsEnterpriseList.stream().collect(Collectors.toMap(DrugsEnterprise::getId,a->a,(k1,k2)->k1));
        List<RecipeOrderRefundVO> recipeOrderRefundVOList = new ArrayList<>();

        recipeOrderList.forEach(recipeOrder -> {
            RecipeOrderRefundVO recipeOrderRefundVO = new RecipeOrderRefundVO();
            recipeOrderRefundVO.setOrderCode(recipeOrder.getOrderCode());
            recipeOrderRefundVO.setActualPrice(recipeOrder.getActualPrice());
            recipeOrderRefundVO.setCreateTime(DateConversion.getDateFormatter(recipeOrder.getCreateTime(), DateConversion.DEFAULT_DATE_TIME));
            if (null != recipeOrder.getEnterpriseId()) {
                if (StringUtils.isNotEmpty(recipeOrder.getDrugStoreName())) {
                    recipeOrderRefundVO.setDepName(recipeOrder.getDrugStoreName());
                } else {
                    recipeOrderRefundVO.setDepName(drugsEnterpriseMap.get(recipeOrder.getEnterpriseId()).getName());
                }
            }
            recipeOrderRefundVO.setPatientName(recipeOrderCodeMap.get(recipeOrder.getOrderCode()).getPatientName());
            recipeOrderRefundVO.setPayModeText(PayModeEnum.getPayModeEnumName(recipeOrder.getPayMode()));
            recipeOrderRefundVO.setGiveModeText(recipeOrder.getGiveModeText());
            recipeOrderRefundVOList.add(recipeOrderRefundVO);
        });
        recipeOrderRefundPageVO.setRecipeOrderRefundVOList(recipeOrderRefundVOList);
        recipeOrderRefundPageVO.setStart(recipeOrderRefundReqVO.getStart());
        recipeOrderRefundPageVO.setLimit(recipeOrderRefundReqVO.getLimit());
        return recipeOrderRefundPageVO;
    }

    @Override
    public RecipeOrderRefundDetailVO getRefundOrderDetail(String orderCode) {
        RecipeOrderRefundDetailVO recipeOrderRefundDetailVO = new RecipeOrderRefundDetailVO();
        RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(orderCode);
        if (null == recipeOrder) {
            return recipeOrderRefundDetailVO;
        }
        RecipeOrderBean recipeOrderBean = ObjectCopyUtils.convert(recipeOrder, RecipeOrderBean.class);
        recipeOrderRefundDetailVO.setRecipeOrderBean(recipeOrderBean);
        List<Integer> recipeIdList = JSONUtils.parse(recipeOrder.getRecipeIdList(), List.class);
        List<Recipe> recipeList = recipeDAO.findByRecipeIds(recipeIdList);
        List<RecipeExtend> recipeExtendList = recipeExtendDAO.queryRecipeExtendByRecipeIds(recipeIdList);
        Map<Integer, RecipeExtend> recipeExtendMap = recipeExtendList.stream().collect(Collectors.toMap(RecipeExtend::getRecipeId,a->a,(k1,k2)->k1));
        List<Recipedetail> recipeDetailList = recipeDetailDAO.findByRecipeIds(recipeIdList);
        Map<Integer, List<Recipedetail>> detailMap = recipeDetailList.stream().collect(Collectors.groupingBy(Recipedetail::getRecipeId));
        List<RecipeBean> recipeBeanList = new ArrayList<>();
        recipeList.forEach(recipe -> {
            RecipeBean recipeBean = ObjectCopyUtils.convert(recipe, RecipeBean.class);
            RecipeExtendBean recipeExtendBean = ObjectCopyUtils.convert(recipeExtendMap.get(recipe.getRecipeId()), RecipeExtendBean.class);
            List<RecipeDetailBean> recipeDetailBeans = ObjectCopyUtils.convert(detailMap.get(recipe.getRecipeId()), RecipeDetailBean.class);
            recipeBean.setRecipeExtend(recipeExtendBean);
            recipeBean.setRecipeDetailBeanList(recipeDetailBeans);
            recipeBeanList.add(recipeBean);
        });
        recipeOrderRefundDetailVO.setRecipeBeanList(recipeBeanList);
        return recipeOrderRefundDetailVO;
    }
}
