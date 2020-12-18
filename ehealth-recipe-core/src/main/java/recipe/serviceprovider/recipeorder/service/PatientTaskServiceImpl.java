package recipe.serviceprovider.recipeorder.service;

import com.ngari.bus.task.module.ModuleInfo;
import com.ngari.bus.task.module.PatientTask;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.recipe.serviceprovider.service.IPatientTaskService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeExtendDAO;
import recipe.service.HisRecipeService;
import recipe.service.manager.EmrRecipeManager;
import recipe.serviceprovider.recipeorder.service.constant.RecipeTaskEnum;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author xlx
 * @Date: 2020/12/14
 * @Description:recipe.serviceprovider.recipeorder.service
 * @version:1.0
 */

@RpcBean("patientTaskService")
public class PatientTaskServiceImpl implements IPatientTaskService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HisRecipeService.class);
    private RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
    private RecipeExtendDAO recipeExtendDAO = DAOFactory.getDAO(RecipeExtendDAO.class);


    /**
     * 首页获取用户需要处理的处方单
     *
     * @param mpiId
     * @param organId
     * @param start
     * @param limit
     * @return
     */
    @Override
    @RpcService
    public List<PatientTask> findPatientTask(String mpiId, Integer organId, Integer start, Integer limit) {
        //打印日志， 校验参数
        LOGGER.info("PatientTaskServiceImpl findPatientTask mpiId={},organId={},start={},limit={}", mpiId, organId, start, limit);
        if (StringUtils.isEmpty(mpiId) && null == organId) {
            LOGGER.warn("PatientTaskServiceImpl findPatientTask mpiId,organId is null");
            throw new DAOException(DAOException.VALUE_NEEDED, "mpiId is null or organId is null");
        }

        List<PatientTask> patientTaskArrayList = new ArrayList<>();
        // 获取对应的处方单
        List<Recipe> recipes = recipeDAO.queryRecipeInfoByMpiIdAndOrganId(mpiId, organId, start, limit);
        if (CollectionUtils.isEmpty(recipes)) {
            return patientTaskArrayList;
        }
        //查询出未支付的订单
        List<RecipeOrder> recipeOrders = recipeDAO.queryOrderCodeUnpaid(mpiId, organId);
        //将list转变为map
        Map<String, RecipeOrder> recipeOrderMap = recipeOrders.stream().collect(Collectors.toMap(RecipeOrder::getOrderCode, a -> a, (k1, k2) -> k1));
        //通过recipe集合获取recipeExtends对象集合
        List<Integer> recipeIds = recipes.stream().map(Recipe::getRecipeId).collect(Collectors.toList());
        List<RecipeExtend> recipeExtends = recipeExtendDAO.queryRecipeExtendByRecipeIds(recipeIds);
        Map<Integer, RecipeExtend> recipeExtendMap = recipeExtends.stream().collect(Collectors.toMap(RecipeExtend::getRecipeId, a -> a));
        for (Recipe recipe : recipes) {
            PatientTask patientTask = new PatientTask();
            Map<String, Object> params = new HashMap<>();
            ModuleInfo moduleInfo = new ModuleInfo();
            //调用电子病历进行获取病情名
            EmrRecipeManager.getMedicalInfo(recipe, recipeExtendMap.get(recipe.getRecipeId()));
            patientTask.setDiseaseName(recipe.getOrganDiseaseName());
            patientTask.setBusType("recipe");
            patientTask.setBusId(recipe.getRecipeId());
            patientTask.setDoctorId(recipe.getDoctor());
            patientTask.setDoctorName(recipe.getDoctorName());
            patientTask.setBusDate(recipe.getSignDate());
            params.put("cid", recipe.getRecipeId());
            params.put("organId", organId);
            params.put("mpiId", mpiId);
            moduleInfo.setParams(params);
            moduleInfo.setInitFn("doHandle");
            moduleInfo.setUrl("eh.wx.health.patientRecipe.RecipeDetail");
            patientTask.setModuleInfo(moduleInfo);
            if (null != recipe.getOrderCode()) {

                //判断处方状态
                RecipeOrder recipeOrder = recipeOrderMap.get(recipe.getOrderCode());
                if (null != recipeOrder) {
                    //待支付
                    patientTask.setTaskName(RecipeTaskEnum.RECIPE_TASK_STATUS_UNPAID.getTaskName());
                    patientTask.setBusStatusName(RecipeTaskEnum.RECIPE_TASK_STATUS_UNPAID.getBusStatusName());
                    patientTask.setButtonName(RecipeTaskEnum.RECIPE_TASK_STATUS_UNPAID.getButtonName());
                    patientTaskArrayList.add(patientTask);
                    continue;
                }
            }
            //处理剩下的状态
            RecipeTaskEnum recipeStatusEnum = RecipeTaskEnum.getRecipeStatusEnum(recipe.getStatus());
            if (RecipeTaskEnum.NONE != recipeStatusEnum) {
                patientTask.setTaskName(recipeStatusEnum.getTaskName());
                patientTask.setBusStatusName(recipeStatusEnum.getBusStatusName());
                patientTask.setButtonName(recipeStatusEnum.getButtonName());
                patientTaskArrayList.add(patientTask);
            }
        }

        LOGGER.info("PatientTaskServiceImpl findPatientTask List<PatientTask>:{}", JSONUtils.toString(patientTaskArrayList));
        return patientTaskArrayList;
    }
}
