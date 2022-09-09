package recipe.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeOrder;
import ctd.persistence.exception.DAOException;
import eh.base.constant.ErrorCode;
import org.springframework.stereotype.Service;
import recipe.aop.LogRecord;
import recipe.enumerate.status.*;

/**
 * 状态处理通用类：处方状态 ，订单状态，审方状态
 *
 * @author fuzi
 */
@Service
public class StateManager extends BaseManager {

    /**
     * 修改订单状态
     *
     * @param orderId     订单id
     * @param processState 父状态枚举
     * @param subState     子状态枚举
     * @return
     */
    @LogRecord
    public Boolean updateOrderState(Integer orderId, OrderStateEnum processState, OrderStateEnum subState) {
        RecipeOrder recipeOrder = recipeOrderDAO.get(orderId);
        logger.info("StateManager updateOrderState recipeOrder:{}", JSON.toJSONString(recipeOrder));
        if (null == recipeOrder) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该订单不存在");
        }
        boolean result;
        switch (processState) {
            case PROCESS_STATE_ORDER:
                result = this.updateOrderStateWithLogistics(recipeOrder, processState, subState);
                break;
            case PROCESS_STATE_CANCELLATION:
            case PROCESS_STATE_DISPENSING:
            case PROCESS_STATE_ORDER_PLACED:
            case PROCESS_STATE_READY_PAY:
            case NONE:
                result = this.defaultOrder(recipeOrder, processState, subState);
                break;
            default:
                result = false;
                break;
        }
        return result;
    }




    /**
     * 修改处方状态
     *
     * @param recipeId     处方id
     * @param processState 父状态枚举
     * @param subState     子状态枚举
     * @return
     */
    @LogRecord
    public Boolean updateRecipeState(Integer recipeId, RecipeStateEnum processState, RecipeStateEnum subState) {
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        logger.info("StateManager updateRecipeState recipe:{},processState{}", JSON.toJSONString(recipe), processState.getType());
        if (null == recipe) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方不存在");
        }
        boolean result;
        switch (processState) {
            case PROCESS_STATE_SUBMIT:
                result = this.submit(recipe, processState);
                break;
            case PROCESS_STATE_AUDIT:
                result = this.audit(recipe, processState, subState);
                break;
            case NONE:
            case PROCESS_STATE_DONE:
            case PROCESS_STATE_DISPENSING:
            case PROCESS_STATE_DISTRIBUTION:
            case PROCESS_STATE_MEDICINE:
                result = this.defaultRecipe(recipe, processState, subState);
                break;
            case PROCESS_STATE_ORDER:
                result = this.readySubmitOrder(recipe, processState, subState);
                break;
            case PROCESS_STATE_DELETED:
            case PROCESS_STATE_CANCELLATION:
                result = this.cancellation(recipe, processState, subState);
                break;
            default:
                result = false;
                break;
        }
        saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), subState.getName());
        return result;
    }

    /**
     * 待购药
     * @param recipe
     * @param processState
     * @param subState
     * @return
     */
    private Boolean readySubmitOrder(Recipe recipe, RecipeStateEnum processState, RecipeStateEnum subState){
        Recipe updateRecipe = new Recipe();
        updateRecipe.setRecipeId(recipe.getRecipeId());
        updateRecipe.setProcessState(processState.getType());
        updateRecipe.setSubState(subState.getType());

        if (RecipeStateEnum.PROCESS_STATE_ORDER == processState && RecipeStateEnum.SUB_ORDER_CANCEL_ORDER == subState) {
            updateRecipe.setProcessState(RecipeStateEnum.PROCESS_STATE_ORDER.getType());
            updateRecipe.setSubState(RecipeStateEnum.SUB_ORDER_READY_SUBMIT_ORDER.getType());
        }
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        return true;
    }

    /**
     * 待审核处方
     *
     * @param recipe
     * @param processState
     * @param subState
     * @return
     */
    private Boolean audit(Recipe recipe, RecipeStateEnum processState, RecipeStateEnum subState) {
        Recipe updateRecipe = new Recipe();
        updateRecipe.setRecipeId(recipe.getRecipeId());
        updateRecipe.setProcessState(processState.getType());
        updateRecipe.setSubState(subState.getType());
        if (RecipeStateEnum.PROCESS_STATE_AUDIT == processState && RecipeStateEnum.SUB_AUDIT_READY_DONE == subState) {
            updateRecipe.setSubState(RecipeStateEnum.NONE.getType());
            updateRecipe.setProcessState(RecipeStateEnum.NONE.getType());
        }
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        return true;
    }

    /**
     * 待提交处方
     *
     * @param recipe
     * @param processState
     * @param
     * @return
     */
    private Boolean submit(Recipe recipe, RecipeStateEnum processState) {
        logger.info("StateManager submit recipeId={} writeHisState:{}，doctorSignState={}"
                , recipe.getRecipeId(), recipe.getWriteHisState(), recipe.getDoctorSignState());
        Recipe updateRecipe = new Recipe();
        updateRecipe.setRecipeId(recipe.getRecipeId());
        updateRecipe.setProcessState(processState.getType());
        RecipeStateEnum sub = RecipeStateEnum.getRecipeStateEnum(recipe.getSubState());
        if (WriteHisEnum.NONE.getType().equals(recipe.getWriteHisState())) {
            sub = RecipeStateEnum.SUB_SUBMIT_TEMPORARY;
        }
        if (WriteHisEnum.WRITE_HIS_STATE_SUBMIT.getType().equals(recipe.getWriteHisState())) {
            sub = RecipeStateEnum.SUB_SUBMIT_CHECKING_HOS;
        }
        if (SignEnum.SIGN_STATE_SUBMIT.getType().equals(recipe.getDoctorSignState())) {
            sub = RecipeStateEnum.SUB_SUBMIT_DOC_SIGN_ING;
        }
        if (SignEnum.SIGN_STATE_AUDIT.getType().equals(recipe.getDoctorSignState())) {
            sub = RecipeStateEnum.SUB_SUBMIT_DOC_SIGN_FAIL;
        }
        if (SignEnum.SIGN_STATE_ORDER.getType().equals(recipe.getDoctorSignState())
                && WriteHisEnum.WRITE_HIS_STATE_ORDER.getType().equals(recipe.getWriteHisState())) {
            updateRecipe.setSubState(RecipeStateEnum.NONE.getType());
            updateRecipe.setProcessState(RecipeStateEnum.NONE.getType());
        }
        updateRecipe.setSubState(sub.getType());
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        return true;
    }


    /**
     * 更新审核状态
     *
     * @param recipeId
     * @param state
     * @return
     */
    @LogRecord
    public Boolean updateAuditState(Integer recipeId, RecipeAuditStateEnum state) {
        Recipe updateRecipe = new Recipe();
        updateRecipe.setRecipeId(recipeId);
        updateRecipe.setAuditState(state.getType());
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        return true;
    }

    /**
     * 更改药师签名状态
     * @param recipeId
     * @param checkerSignState
     * @return
     */
    @LogRecord
    public Boolean updateCheckerSignState(Integer recipeId, SignEnum checkerSignState) {
        Recipe updateRecipe = new Recipe();
        updateRecipe.setRecipeId(recipeId);
        updateRecipe.setCheckerSignState(checkerSignState.getType());
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        return true;
    }

    /**
     * 兼容修改老状态
     *
     * @param recipeId
     * @param status
     * @return
     */
    @LogRecord
    public Boolean updateStatus(Integer recipeId, RecipeStatusEnum status, SignEnum sign) {
        Recipe updateRecipe = new Recipe();
        updateRecipe.setRecipeId(recipeId);
        updateRecipe.setStatus(status.getType());
        if (null != sign) {
            updateRecipe.setDoctorSignState(sign.getType());
        }
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        return true;
    }

    /**
     * 作废订单
     *
     * @param order        订单信息
     * @param processState 父状态枚举
     * @param subState     子状态枚举
     * @return
     */
    private Boolean defaultOrder(RecipeOrder order, OrderStateEnum processState, OrderStateEnum subState) {
        RecipeOrder updateOrder = new RecipeOrder(order.getOrderId(),processState.getType(),subState.getType());
        logger.info("updateOrder:{}",JSONArray.toJSONString(updateOrder));
        updateOrder.setOrderId(order.getOrderId());
        updateOrder.setProcessState(processState.getType());
        updateOrder.setSubState(subState.getType());
        recipeOrderDAO.updateNonNullFieldByPrimaryKey(updateOrder);
        return true;
    }

    /**
     * 作废处方
     *
     * @param recipe       处方信息
     * @param processState 父状态枚举
     * @param subState     子状态枚举
     * @return
     */
    private Boolean cancellation(Recipe recipe, RecipeStateEnum processState, RecipeStateEnum subState) {
        if (RecipeStateEnum.PROCESS_STATE_DELETED == processState && recipe.getProcessState() > 1) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "该处方单不是暂存处方不能删除");
        }
        if (RecipeStateEnum.PROCESS_STATE_DELETED.getType().equals(recipe.getProcessState())
                || RecipeStateEnum.PROCESS_STATE_CANCELLATION.getType().equals(recipe.getProcessState())) {
            return true;
        }

        Recipe updateRecipe = new Recipe();
        //药师审核未通过 只要不是审核不通过
        if (RecipeStateEnum.SUB_CANCELLATION_AUDIT_NOT_PASS != subState
                && RecipeAuditStateEnum.PENDING_REVIEW.getType().equals(recipe.getAuditState())) {
            updateRecipe.setAuditState(RecipeAuditStateEnum.NO_REVIEW.getType());
        }
        updateRecipe.setRecipeId(recipe.getRecipeId());
        updateRecipe.setProcessState(processState.getType());
        updateRecipe.setSubState(subState.getType());
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        return true;
    }

    /**
     * 更改处方新状态走默认
     * @param recipe
     * @param processState
     * @param subState
     * @return
     */
    private boolean defaultRecipe(Recipe recipe, RecipeStateEnum processState, RecipeStateEnum subState) {
        Recipe updateRecipe = new Recipe();
        updateRecipe.setRecipeId(recipe.getRecipeId());
        updateRecipe.setProcessState(processState.getType());
        updateRecipe.setSubState(subState.getType());
        recipeDAO.updateNonNullFieldByPrimaryKey(updateRecipe);
        return true;
    }

    /**
     * 修改订单与物流状态(处理中订单)
     * @param order
     * @param processState
     * @param subState
     * @return
     */
    private boolean updateOrderStateWithLogistics(RecipeOrder order, OrderStateEnum processState, OrderStateEnum subState) {
        RecipeOrder updateOrder = new RecipeOrder(order.getOrderId(),processState.getType(),subState.getType());
        logger.info("updateOrder:{}",JSONArray.toJSONString(updateOrder));
        updateOrder.setOrderId(order.getOrderId());
        updateOrder.setProcessState(processState.getType());
        updateOrder.setSubState(subState.getType());
        Integer logisticsStateType = OrderLogisticsStateEnum.NONE.getType();
        switch (subState){
            case SUB_ORDER_DELIVERED_MEDICINE:
                logisticsStateType = OrderLogisticsStateEnum.LOGISTICS_STATE_DISPENSING.getType();
                break;
            case SUB_ORDER_DELIVERED:
                logisticsStateType = OrderLogisticsStateEnum.LOGISTICS_STATE_DISTRIBUTION.getType();
                break;
            case SUB_ORDER_TAKE_MEDICINE:
                logisticsStateType = OrderLogisticsStateEnum.LOGISTICS_STATE_MEDICINE.getType();
                break;
            default:
                break;
        }
        updateOrder.setLogisticsState(logisticsStateType);
        recipeOrderDAO.updateNonNullFieldByPrimaryKey(updateOrder);
        return true;

    }
}
