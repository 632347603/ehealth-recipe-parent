package recipe.service;

import com.google.common.collect.Maps;
import com.ngari.base.push.model.SmsInfoBean;
import com.ngari.base.push.service.ISmsPushService;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.OrganService;
import com.ngari.recipe.entity.OrganDrugList;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.hisprescription.model.HosRecipeResult;
import com.ngari.recipe.hisprescription.model.HospitalStatusUpdateDTO;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import ctd.persistence.DAOFactory;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.ApplicationUtils;
import recipe.constant.*;
import recipe.dao.OrganDrugListDAO;
import recipe.dao.RecipeDAO;
import recipe.drugsenterprise.ThirdEnterpriseCallService;
import recipe.recipecheck.HisCheckRecipeService;
import recipe.service.common.RecipeCacheService;
import recipe.service.hospitalrecipe.PrescribeService;
import recipe.serviceprovider.recipe.service.RemoteRecipeService;
import recipe.util.DateConversion;
import recipe.util.LocalStringUtil;
import recipe.util.RedisClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 电子处方定时任务服务
 *
 * @author yuyun
 */
@RpcBean(value = "recipeTimedTaskService")
public class RecipeTimedTaskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeTimedTaskService.class);

    private static final String HIS_RECIPE_KEY_PREFIX = "hisRecipe_";

    /**
     * 在审核成功后的第1小时、第24小时、48小时，处方失效前1小时这几个节点
     */
    private static final List<Long> HOURS = Arrays.asList(1L, 24L, 48L);

    @Autowired
    private RemoteRecipeService remoteRecipeService;
    @Autowired
    private RecipeDAO recipeDAO;
    @Autowired
    private ISmsPushService smsPushService;

    /**
     * 定时任务 钥匙圈处方 配送中状态 持续一周后系统自动完成该笔业务
     */
    @RpcService
    public void autoFinishRecipeTask() {
        String endDt =
                DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(RecipeSystemConstant.ONE_WEEK_AGO),
                        DateConversion.DEFAULT_DATE_TIME);
        String startDt =
                DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(
                        RecipeSystemConstant.ONE_MONTH_AGO), DateConversion.DEFAULT_DATE_TIME);

        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Recipe> recipes = recipeDAO.findNotConfirmReceiptList(startDt, endDt);

        ThirdEnterpriseCallService service = ApplicationUtils.getRecipeService(
                ThirdEnterpriseCallService.class, "takeDrugService");

        if (null != recipes && recipes.size() > 0) {
            for (Recipe recipe : recipes) {
                Map<String, Object> paramMap = Maps.newHashMap();
                paramMap.put("recipeId", recipe.getRecipeId());
                paramMap.put("sender", "systemTask");
                service.finishRecipe(paramMap);
            }
        }
        LOGGER.info("autoFinishRecipeTask size={}", null == recipes ? "null" : recipes.size());

    }

    /**
     * 定时任务 每天12:10点定时将redis里的处方和处方详情保存到数据库
     * 保存邵逸夫his传过来的处方数据
     */
    @RpcService
    public void autoSaveRecipeByRedis() {
        RedisClient redisClient = RedisClient.instance();
        RecipeBean recipeBean;
        List<RecipeDetailBean> recipeDetailBeans;
        Map<String, Object> objectMap;
        //遍历redis里带有hisRecipe_前缀的所有keys
        Set<String> keys = null;
        try {
            keys = redisClient.scan(HIS_RECIPE_KEY_PREFIX + "*");
        } catch (Exception e) {
            LOGGER.error("redis error" + e.toString());
            return;
        }
        //取出每一个key对应的map
        Map<String, Object> map;
        if (null != keys && keys.size() > 0) {
            for (String key : keys) {
                map = redisClient.hScan(key, 10000, "*");
                LOGGER.info("autoSaveRecipeByRedis key={} map.size={}",key,map.size());
                int num = 0;//统计保存成功的数量
                //遍历map取出value
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    objectMap = (Map<String, Object>) entry.getValue();
                    //取到需要保存处方单和处方详情，save到数据库
                    recipeBean = (RecipeBean) objectMap.get("recipeBean");
                    recipeDetailBeans = (List<RecipeDetailBean>) objectMap.get("recipeDetailBeans");
                    boolean flag = true;
                    try {
                        remoteRecipeService.saveRecipeDataFromPayment(recipeBean, recipeDetailBeans);
                    } catch (Exception e) {
                        LOGGER.error("recipeService.saveRecipeDataFromPayment error" + e.toString());
                        flag = false;
                    }finally {
                        if (flag){
                            num++;
                        }
                    }
                }
                LOGGER.info("autoSaveRecipeByRedis key={} saveSuccess={}",key,num);
                //删除redis key
                redisClient.del(key);
            }
        }

    }

    /**
     * 定时任务 his回调失败(医院确认中)5分钟后确保流程继续(更新为待审核状态) but 杭州互联网模式不包含在内
     * 每5分钟执行一次 优化成每一分钟执行一次
     * 优化：需将5分钟优化为1.5分钟 --20191223---//最快1分钟 最慢2分钟  平均一下1.5分钟
     */
    @RpcService
    public void updateRecipeStatus(){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        RecipeCacheService recipeService = ApplicationUtils.getRecipeService(RecipeCacheService.class);
        String updateRecipeStatusTime = recipeService.getRecipeParam("updateRecipeStatusTime", "1");
        //获取参数表设置的几分钟前的时间
        Calendar now = Calendar.getInstance();
        now.setTime(new Date());
        now.add(Calendar.MINUTE, -(Integer.valueOf(updateRecipeStatusTime)));
        Date time = now.getTime();
        //设置查询时间段
        String endDt = DateConversion.getDateFormatter(time, DateConversion.DEFAULT_DATE_TIME);
        String startDt = DateConversion.getDateFormatter(DateConversion.getDateTimeDaysAgo(1), DateConversion.DEFAULT_DATE_TIME);

        List<Recipe> recipeList = recipeDAO.findRecipeListForStatus(RecipeStatusConstant.CHECKING_HOS, startDt, endDt);
        if (CollectionUtils.isNotEmpty(recipeList)) {
            PrescribeService prescribeService = ApplicationUtils.getRecipeService(
                    PrescribeService.class, "remotePrescribeService");
            OrganService organService = BasicAPI.getService(OrganService.class);
            Map<String, String> otherInfo = Maps.newHashMap();
            for (Recipe recipe : recipeList) {
                //处方流转模式是否是互联网模式并且不是杭州互联网模式
                if (RecipeBussConstant.RECIPEMODE_ZJJGPT.equals(recipe.getRecipeMode())&&(RecipeServiceSub.isNotHZInternet(recipe.getClinicOrgan()))){
                    HospitalStatusUpdateDTO hospitalStatusUpdateDTO = new HospitalStatusUpdateDTO();
                    hospitalStatusUpdateDTO.setOrganId(organService.getOrganizeCodeByOrganId(recipe.getClinicOrgan()));
                    hospitalStatusUpdateDTO.setRecipeCode(recipe.getRecipeCode());
                    hospitalStatusUpdateDTO.setStatus(LocalStringUtil.toString(RecipeStatusConstant.CHECK_PASS));
                    otherInfo.put("distributionFlag", "1");
                    HosRecipeResult result = prescribeService.updateRecipeStatus(hospitalStatusUpdateDTO, otherInfo);
                    LOGGER.info("updateRecipeStatus,recipeId={} result={}",recipe.getRecipeId(), JSONUtils.toString(result));
                }
            }
        }
    }

    /**
     * 通知前置机去HIS查询审方状态
     */
    @RpcService
    public void noticeGetHisCheckStatusTask(){
        RecipeDAO recipeDAO = DAOFactory.getDAO(RecipeDAO.class);
        List<Recipe> recipes = recipeDAO.findReadyCheckRecipeByCheckMode(2);
        for (Recipe recipe : recipes) {
            //针对his审方的模式,先在此处处理,推送消息给前置机,让前置机取轮询HIS获取审方结果
            HisCheckRecipeService hisCheckRecipeService = ApplicationUtils.getRecipeService(HisCheckRecipeService.class);
            hisCheckRecipeService.sendCheckRecipeInfo(recipe);
        }

        try{
            OrganDrugListDAO drugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
            List<OrganDrugList> organDrugLists = drugListDAO.findOrganDrug(0, 1);
            if (CollectionUtils.isNotEmpty(organDrugLists)) {
                OrganDrugList organDrugList = organDrugLists.get(0);
                Date lastModify = organDrugList.getLastModify();
                organDrugList.setLastModify(new Date());
                drugListDAO.update(organDrugList);
                organDrugList.setLastModify(lastModify);
                drugListDAO.update(organDrugList);
            }
        } catch (Exception e) {
            LOGGER.info("RecipeTimedTaskService.noticeGetHisCheckStatusTask 更新异常{}", e.getMessage());
        }
    }

    /**
     * 39462 【杭州市互联网医院】处方审核完成后，患者端增加消息提醒功能
     */
    @RpcService
    public void pushPay() {
        List<Recipe> recipeList = recipeDAO.findByPayFlagAndReviewType(PayConstant.PAY_FLAG_NOT_PAY, ReviewTypeConstant.Preposition_Check);
        if (CollectionUtils.isEmpty(recipeList)) {
            return;
        }
        LOGGER.info("RecipeTimedTaskService pushPay recipeList = {}", recipeList.size());
        LocalDateTime date = LocalDateTime.now();

        for (Recipe recipe : recipeList) {
            if (null == recipe.getCreateDate() || null == recipe.getValueDays()) {
                LOGGER.warn("RecipeTimedTaskService pushPay date is error recipe = {}", JSONUtils.toString(recipe));
                continue;
            }
            //开方时间
            LocalDateTime createDate = Instant.ofEpochMilli(recipe.getCreateDate().getTime()).atZone(ZoneId.systemDefault()).toLocalDateTime();
            Duration create = Duration.between(date, createDate);
            Long createHour = create.toHours();
            //失效时间计算
            LocalDateTime failureDate = createDate.plusDays(recipe.getValueDays());
            Duration failure = Duration.between(date, failureDate);
            long failureHour = failure.toHours();
            if (HOURS.contains(createHour) || 1L == failureHour) {
                LOGGER.debug("RecipeTimedTaskService pushPay recipe = {}", JSONUtils.toString(recipe));
                //发消息
                SmsInfoBean smsInfo = new SmsInfoBean();
                smsInfo.setBusId(recipe.getRecipeId());
                smsInfo.setOrganId(recipe.getClinicOrgan());
                smsInfo.setBusType("RecipePushPay");
                smsInfo.setSmsType("RecipePushPay");
                smsPushService.pushMsgData2OnsExtendValue(smsInfo);
            }
        }
    }
}
