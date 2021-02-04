package recipe.service;

import com.alibaba.fastjson.JSON;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.base.PatientBaseInfo;
import com.ngari.his.recipe.mode.*;
import com.ngari.his.recipe.service.IRecipeHisService;
import com.ngari.patient.dto.AppointDepartDTO;
import com.ngari.patient.dto.EmploymentDTO;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.service.*;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.HisRecipeDetailVO;
import com.ngari.recipe.recipe.model.HisRecipeVO;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.revisit.RevisitAPI;
import com.ngari.revisit.common.model.RevisitExDTO;
import com.ngari.revisit.common.service.IRevisitExService;
import ctd.persistence.DAOFactory;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import ctd.util.event.GlobalEventExecFactory;
import eh.base.constant.ErrorCode;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import recipe.ApplicationUtils;
import recipe.constant.OrderStatusConstant;
import recipe.constant.PayConstant;
import recipe.constant.RecipeBussConstant;
import recipe.constant.RecipeStatusConstant;
import recipe.dao.*;
import recipe.service.manager.EmrRecipeManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;


/**
 * @author yinsheng
 * @date 2020\3\10 0010 19:58
 */
@RpcBean(value = "hisRecipeService", mvc_authentication = false)
public class HisRecipeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HisRecipeService.class);

    @Autowired
    private HisRecipeDAO hisRecipeDAO;
    @Autowired
    private HisRecipeExtDAO hisRecipeExtDAO;
    @Autowired
    private HisRecipeDetailDAO hisRecipeDetailDAO;
    @Autowired
    private RecipeDAO recipeDAO;
    @Autowired
    private RecipeOrderDAO recipeOrderDAO;
    @Autowired
    private RecipeExtendDAO recipeExtendDAO;
    @Autowired
    private RecipeDetailDAO recipeDetailDAO;

    @Autowired
    private EmrRecipeManager emrRecipeManager;
    private static final ThreadLocal<String> recipeCodeThreadLocal = new ThreadLocal<String>();

    /**
     * organId 机构编码
     * mpiId 用户mpiId
     * timeQuantum 时间段  1 代表一个月  3 代表三个月 6 代表6个月
     * status 1 未处理 2 已处理
     *
     * @param request
     * @return
     */
    @RpcService
    public List<HisRecipeVO> findHisRecipe(Map<String, Object> request) {
        Integer organId = (Integer) request.get("organId");
        String mpiId = (String) request.get("mpiId");
        Integer timeQuantum = (Integer) request.get("timeQuantum");
        String status = (String) request.get("status");
        String cardId = (String) request.get("cardId");
        Integer start = (Integer) request.get("start");
        Integer limit = (Integer) request.get("limit");
        start=0;limit=1000;
        Integer flag = 1;
        if (!"ongoing".equals(status)) {
            flag = 2;
        }
        PatientService patientService = BasicAPI.getService(PatientService.class);
        PatientDTO patientDTO = patientService.getPatientBeanByMpiId(mpiId);
        if (StringUtils.isNotEmpty(cardId)) {
            patientDTO.setCardId(cardId);
        } else {
            patientDTO.setCardId("");
        }
        if (null == patientDTO) {
            throw new DAOException(609, "患者信息不存在");
        }
        //同步查询待缴费处方[只查询，不存储]
        HisResponseTO<List<QueryHisRecipResTO>> noPayFeeRecipes=queryData(organId, patientDTO, timeQuantum, 1);
        //待缴费非本人同步处方处理
        dealPatientInfo(noPayFeeRecipes,patientDTO);
        //异步获取已缴费处方
        //RecipeBusiThreadPool.submit(new QueryHisRecipeCallable(organId, mpiId, timeQuantum, 2, patientDTO));
        List<HisRecipe> hisRecipes = hisRecipeDAO.findHisRecipes(organId, mpiId, flag, start, limit);
        LOGGER.info("findHisRecipe  hisRecipes:{},organId:{},mpiId:{},flag:{},start:{},limit:{}", JSONUtils.toString(hisRecipes), organId, mpiId, flag, start, limit);
        //数据合并
        List<HisRecipeVO> result =mergeData(flag,noPayFeeRecipes,hisRecipes,patientDTO);
        LOGGER.info("findHisRecipe mergeData result:{},organId:{},mpiId:{},flag:{},start:{},limit:{}", JSONUtils.toString(result), organId, mpiId, flag, start, limit);
        return result;
    }

    /**
     * 待缴费非本人同步处方处理
     * @param noPayFeeRecipe
     * @param patientDTO
     */
    private void dealPatientInfo(HisResponseTO<List<QueryHisRecipResTO>> noPayFeeRecipe, PatientDTO patientDTO) {
        LOGGER.info("dealPatientInfo noPayFeeRecipe:{} patientDTO:{}",JSONUtils.toString(noPayFeeRecipe),JSONUtils.toString(patientDTO));
        List<HisRecipeVO> noPayFeeHisRecipeVO=covertToHisRecipeObject(noPayFeeRecipe, patientDTO, 1);
        for(HisRecipeVO noPayFeeHisRecipeVOHisRecipeVO :noPayFeeHisRecipeVO ){
            HisRecipe hisRecipe = hisRecipeDAO.getHisRecipeByRecipeCodeAndClinicOrgan(noPayFeeHisRecipeVOHisRecipeVO.getClinicOrgan(), noPayFeeHisRecipeVOHisRecipeVO.getRecipeCode());
            Recipe haveRecipe = recipeDAO.getByHisRecipeCodeAndClinicOrgan(noPayFeeHisRecipeVOHisRecipeVO.getRecipeCode(), noPayFeeHisRecipeVOHisRecipeVO.getClinicOrgan());
            LOGGER.info("dealPatientInfo haveRecipe:{}",JSONUtils.toString(haveRecipe) );
            //如果处方已经转到cdr_recipe表并且支付状态为待支付并且非本人转储到cdr_recipe，
            if (haveRecipe != null) {
                
                if(new Integer(0).equals(haveRecipe.getPayFlag())
                        &&!StringUtils.isEmpty(patientDTO.getMpiId())
                        &&!patientDTO.getMpiId().equals(haveRecipe.getMpiid())){
                    //修改处方患者信息
                    haveRecipe.setMpiid(patientDTO.getMpiId());
                    haveRecipe.setPatientName(patientDTO.getPatientName());
                    haveRecipe.setPatientID(noPayFeeHisRecipeVOHisRecipeVO.getPatientNumber());
                    recipeDAO.update(haveRecipe);
                }
            }
        }
        //修改线下处方患者信息
        saveHisRecipeInfo(noPayFeeRecipe, patientDTO, 1);
    }

    /**
     * @param
     * @param noPayFeeRecipe
     * @param hisRecipes
     * @param patientDTO
     * @author liumin
     * @Description 合并数据
     * @return
     */
    private List<HisRecipeVO> mergeData(Integer flag, HisResponseTO<List<QueryHisRecipResTO>> noPayFeeRecipe, List<HisRecipe> hisRecipes, PatientDTO patientDTO) {
        List<HisRecipeVO> result = new ArrayList<>();
        //根据status状态查询处方列表
        if (new Integer(1).equals(flag)) {
            //待处理
            //noPayFeeRecipe转平台HisRecipeVO对象
            List<HisRecipeVO> noPayFeeHisRecipeVO=covertToHisRecipeObject(noPayFeeRecipe, patientDTO, flag);
            //hisRecipe转平台HisRecipeVO对象
            List<HisRecipeVO> hisRecipeVOs=findPendingHisRecipeVo(hisRecipes);
            List<HisRecipeVO> equalsHisRecipeVOs=new ArrayList<>();
            List<HisRecipeVO> onlyExistnoPayFeeHisRecipeVOs=new ArrayList<>();
            List<HisRecipeVO> onlyExistHisRecipeVOs=new ArrayList<>();

            //遍历his返回待缴费处方
            for(HisRecipeVO noPayFeeHisRecipeVOHisRecipeVO :noPayFeeHisRecipeVO ){
                String noPayFeeHisRecipeVOKey=noPayFeeHisRecipeVOHisRecipeVO.getMpiId()+noPayFeeHisRecipeVOHisRecipeVO.getClinicOrgan()+noPayFeeHisRecipeVOHisRecipeVO.getRecipeCode();
                Boolean isEquals=false;
                //遍历已存到 cdr_his_recipe表处方
                for(HisRecipeVO hisRecipeVO:hisRecipeVOs){
                    String hisRecipeVoKey=hisRecipeVO.getMpiId()+hisRecipeVO.getClinicOrgan()+hisRecipeVO.getRecipeCode();
                    if(!StringUtils.isEmpty(noPayFeeHisRecipeVOKey)){
                        if(noPayFeeHisRecipeVOKey.equals(hisRecipeVoKey)){
                            //处方在cdr_his_recipe存在，在his存在，则取his
                            equalsHisRecipeVOs.add(noPayFeeHisRecipeVOHisRecipeVO);
                            isEquals=true;
                            continue;
                        }
                    }
                }
                if(!isEquals){
                    //处方在cdr_his_recipe不存在，在his存在，则add his
                    onlyExistnoPayFeeHisRecipeVOs.add(noPayFeeHisRecipeVOHisRecipeVO);
                }
            }

            for(HisRecipeVO hisRecipeVO:hisRecipeVOs){
                String hisRecipeVoKey=hisRecipeVO.getMpiId()+hisRecipeVO.getClinicOrgan()+hisRecipeVO.getRecipeCode();
                Boolean isEquals=false;
                for(HisRecipeVO noPayFeeHisRecipeVOHisRecipeVO :noPayFeeHisRecipeVO ){
                    String noPayFeeHisRecipeVOKey=noPayFeeHisRecipeVOHisRecipeVO.getMpiId()+noPayFeeHisRecipeVOHisRecipeVO.getClinicOrgan()+noPayFeeHisRecipeVOHisRecipeVO.getRecipeCode();
                    if(!StringUtils.isEmpty(noPayFeeHisRecipeVOKey)){
                        if(noPayFeeHisRecipeVOKey.equals(hisRecipeVoKey)){
                            isEquals=true;
                            continue;
                        }
                    }
                }
                if (!isEquals) {
                    //处方在cdr_his_recipe存在，在his不存在，则remove cdr_his_recipe data 并删除存储到平台的数据
                    LOGGER.info("HisRecipeService mergeData:hisRecipeVoKey = {},noPayFeeHisRecipeVO={}", hisRecipeVoKey, JSON.toJSONString(noPayFeeHisRecipeVO));
                    onlyExistHisRecipeVOs.add(hisRecipeVO);
                }
            }
            result.addAll(equalsHisRecipeVOs);
            result.addAll(onlyExistnoPayFeeHisRecipeVOs);
            GlobalEventExecFactory.instance().getExecutor().execute(()->{
                 deleteOnlyExistnoHisRecipeVOs(onlyExistHisRecipeVOs);
            });
        } else {
            //已处理
            result=findAlreadyDealHisRecipe(hisRecipes);
        }
        return result;
    }

    /**
     *
     * @param onlyExistnoHisRecipeVOs
     * @author liumin
     * @Description 删除
     */
    private void deleteOnlyExistnoHisRecipeVOs(List<HisRecipeVO> onlyExistnoHisRecipeVOs) {
        LOGGER.info("deleteOnlyExistnoHisRecipeVOs onlyExistnoHisRecipeVOs = {}",  JSONUtils.toString(onlyExistnoHisRecipeVOs));
        if (CollectionUtils.isEmpty(onlyExistnoHisRecipeVOs)) {
            return;
        }
        List<String> recipeCodes = onlyExistnoHisRecipeVOs.stream().map(HisRecipeVO::getRecipeCode).collect(Collectors.toList());
        //查询未支付的处方
        List<Recipe> recipeList = recipeDAO.findByRecipeCodeAndClinicOrganAndMpiid(recipeCodes, onlyExistnoHisRecipeVOs.get(0).getClinicOrgan(),onlyExistnoHisRecipeVOs.get(0).getMpiId());
        if (!CollectionUtils.isEmpty(recipeList)) {
            //delete recipe相关（未支付）
            List<Integer> recipeIds = recipeList.stream().map(Recipe::getRecipeId).collect(Collectors.toList());
            recipeExtendDAO.deleteByRecipeIds(recipeIds);
            recipeDetailDAO.deleteByRecipeIds(recipeIds);
            recipeDAO.deleteByRecipeIds(recipeIds);
            //delete order相关（未支付）
            List<String> orderCodeList = recipeList.stream().filter(a -> StringUtils.isNotEmpty(a.getOrderCode())).map(Recipe::getOrderCode).collect(Collectors.toList());
            if (CollectionUtils.isNotEmpty(orderCodeList)) {
                recipeOrderDAO.deleteByRecipeIds(orderCodeList);
                LOGGER.info("HisRecipeService deleteOnlyExistnoHisRecipeVOs recipeList= {}", JSON.toJSONString(recipeList));
            }
        }


        //查询非已支付线下处方（没转Recipe+转Recipe非已支付）
        List<Integer> hisRecipeIds = hisRecipeDAO.findHisRecipeByPayFlag(recipeCodes, onlyExistnoHisRecipeVOs.get(0).getClinicOrgan(),onlyExistnoHisRecipeVOs.get(0).getMpiId());
        //delete hisRecipe相关
        //List<Integer> hisRecipeIds = onlyExistnoHisRecipeVOs.stream().map(HisRecipeVO::getHisRecipeID).collect(Collectors.toList());
        if(!CollectionUtils.isEmpty(hisRecipeIds)){
            hisRecipeExtDAO.deleteByHisRecipeIds(hisRecipeIds);
            hisRecipeDetailDAO.deleteByHisRecipeIds(hisRecipeIds);
            hisRecipeDAO.deleteByHisRecipeIds(hisRecipeIds);
        }
        LOGGER.info("deleteOnlyExistnoHisRecipeVOs is delete end ");
    }


    /**
     *
     * @param hisRecipes
     * @Author liumin
     * @Description 查询已处理线下处方
     * @return
     */
    private List<HisRecipeVO> findAlreadyDealHisRecipe(List<HisRecipe> hisRecipes) {
        List<HisRecipeVO> result = new ArrayList<>();
        for (HisRecipe hisRecipe : hisRecipes) {
            HisRecipeVO hisRecipeVO = ObjectCopyUtils.convert(hisRecipe, HisRecipeVO.class);
            List<HisRecipeDetail> hisRecipeDetails = hisRecipeDetailDAO.findByHisRecipeId(hisRecipe.getHisRecipeID());
            List<HisRecipeDetailVO> hisRecipeDetailVOS = ObjectCopyUtils.convert(hisRecipeDetails, HisRecipeDetailVO.class);
            hisRecipeVO.setRecipeDetail(hisRecipeDetailVOS);
            hisRecipeVO.setIsCachePlatform(1);
            Recipe recipe = recipeDAO.getByHisRecipeCodeAndClinicOrgan(hisRecipe.getRecipeCode(), hisRecipes.get(0).getClinicOrgan());
            if (recipe == null) {
                //表示该处方单患者在his线下已完成
                hisRecipeVO.setStatusText("已完成");
                hisRecipeVO.setOrderStatusText("已完成");
                hisRecipeVO.setFromFlag(1);
                hisRecipeVO.setJumpPageType(0);
                result.add(hisRecipeVO);
            } else {
                RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
                EmrRecipeManager.getMedicalInfo(recipe, recipeExtend);
                if (StringUtils.isEmpty(recipe.getOrderCode())) {
                    hisRecipeVO.setStatusText(getRecipeStatusTabText(recipe.getStatus()));
                    if (recipeExtend != null && recipeExtend.getFromFlag() == 0) {
                        hisRecipeVO.setFromFlag(1);
                        hisRecipeVO.setJumpPageType(0);
                        result.add(hisRecipeVO);

                    } else {
                        hisRecipeVO.setFromFlag(0);
                        hisRecipeVO.setOrganDiseaseName(recipe.getOrganDiseaseName());
                        hisRecipeVO.setHisRecipeID(recipe.getRecipeId());
                        List<HisRecipeDetailVO> recipeDetailVOS = getHisRecipeDetailVOS(recipe);
                        hisRecipeVO.setRecipeDetail(recipeDetailVOS);
                        hisRecipeVO.setJumpPageType(0);
                        result.add(hisRecipeVO);
                    }
                } else {
                    RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
                    hisRecipeVO.setStatusText(getTipsByStatusForPatient(recipe, recipeOrder));
                    hisRecipeVO.setOrderCode(recipe.getOrderCode());
                    hisRecipeVO.setFromFlag(recipe.getRecipeSourceType()==2?1:0);
                    if (recipe.getFromflag() != 0) {
                        hisRecipeVO.setOrganDiseaseName(recipe.getOrganDiseaseName());
                        List<HisRecipeDetailVO> recipeDetailVOS = getHisRecipeDetailVOS(recipe);
                        hisRecipeVO.setRecipeDetail(recipeDetailVOS);
                    }
                    hisRecipeVO.setJumpPageType(1);
                    result.add(hisRecipeVO);
                }
            }
        }
        return result;
    }

    /**
     * @param hisRecipes
     * @Author liumin
     * @Description 查询待处理线下处方
     * @return
     */
    private List<HisRecipeVO> findPendingHisRecipeVo(List<HisRecipe> hisRecipes) {
        LOGGER.info("findPendingHisRecipeVo:{} ",JSONUtils.toString(hisRecipes));
        List<HisRecipeVO> result = new ArrayList<>();
        for (HisRecipe hisRecipe : hisRecipes) {
            HisRecipeVO hisRecipeVO = ObjectCopyUtils.convert(hisRecipe, HisRecipeVO.class);
            List<HisRecipeDetail> hisRecipeDetails = hisRecipeDetailDAO.findByHisRecipeId(hisRecipe.getHisRecipeID());
            List<HisRecipeDetailVO> hisRecipeDetailVOS = ObjectCopyUtils.convert(hisRecipeDetails, HisRecipeDetailVO.class);
            LOGGER.info("hisRecipeId:{} hisRecipeDetailVOS:{}",hisRecipe.getHisRecipeID(),hisRecipeDetailVOS);
            hisRecipeVO.setRecipeDetail(hisRecipeDetailVOS);
            hisRecipeVO.setOrganDiseaseName(hisRecipe.getDiseaseName());
            hisRecipeVO.setIsCachePlatform(1);
            setOtherInfo(hisRecipeVO,hisRecipe.getMpiId(), hisRecipe.getRecipeCode(), hisRecipe.getClinicOrgan());
            result.add(hisRecipeVO);
//            Recipe recipe = recipeDAO.getByHisRecipeCodeAndClinicOrgan(hisRecipe.getRecipeCode(), hisRecipes.get(0).getClinicOrgan());
//            if (recipe == null) {
//                hisRecipeVO.setOrderStatusText("待支付");
//                hisRecipeVO.setFromFlag(1);
//                hisRecipeVO.setJumpPageType(0);
//                result.add(hisRecipeVO);
//            } else {
//                RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
//                    if (recipeExtend != null && recipeExtend.getFromFlag() == 0) {
//                        //表示该处方来源于HIS
//                        if(StringUtils.isEmpty(recipe.getOrderCode())){
//                            hisRecipeVO.setOrderStatusText("待支付");
//                            hisRecipeVO.setJumpPageType(0);
//                        }else{
//                            RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
//                            if(recipeOrder!=null){
//                                if(new Integer(0).equals(recipeOrder.getPayFlag())){
//                                    hisRecipeVO.setOrderStatusText("待支付");
//                                }else{
//                                    hisRecipeVO.setOrderStatusText("已完成");
//                                }
//                                hisRecipeVO.setJumpPageType(1);//跳转到订单详情页
//                                hisRecipeVO.setStatusText(getTipsByStatusForPatient(recipe, recipeOrder));
//                                hisRecipeVO.setOrderCode(recipe.getOrderCode());
//                            }
//                        }
//                        hisRecipeVO.setFromFlag(recipe.getRecipeSourceType()==2?1:0);
//                        result.add(hisRecipeVO);
//                    } else {
//                        //表示该处方来源于平台
//                        hisRecipeVO.setOrderStatusText("待支付");
//                        hisRecipeVO.setFromFlag(0);
//                        hisRecipeVO.setJumpPageType(0);
//                        hisRecipeVO.setOrganDiseaseName(recipe.getOrganDiseaseName());
//                        hisRecipeVO.setHisRecipeID(recipe.getRecipeId());
//                        List<HisRecipeDetailVO> recipeDetailVOS = getHisRecipeDetailVOS(recipe);
//                        hisRecipeVO.setRecipeDetail(recipeDetailVOS);
//                        result.add(hisRecipeVO);
//                    }
//                }

        }
        return result;
    }

//    private List<HisRecipeVO> convertResult(List<HisRecipeVO> result){
//        if(result!=null&&result.size()>0){
//            //获取运营平台隐方配置
//            IConfigurationCenterUtilsService configService = BaseAPI.getService(IConfigurationCenterUtilsService.class);
//            Object isHiddenRecipeDetail = configService.getConfiguration(result.get(0).getClinicOrgan(), "isHiddenRecipeDetail");
//            for(int i=0;i<result.size();i++){
//                if(recipeListService.isReturnRecipeDetail(result.get(i).getClinicOrgan(),result.get(i).getRecipeType(),0)){
//                }else{
//                    List<HisRecipeDetailVO> hisRecipeDetailVOs=result.get(i).getRecipeDetail();
//                    if(hisRecipeDetailVOs!=null&&hisRecipeDetailVOs.size()>0){
//                        for(int j=0;j<hisRecipeDetailVOs.size();j++){
//                            hisRecipeDetailVOs.get(j).setDrugName(null);
//                            hisRecipeDetailVOs.get(j).setDrugSpec(null);
//                        }
//                    }
//                }
//                result.get(i).setIsHiddenRecipeDetail((boolean)isHiddenRecipeDetail);
//            }
//        }
//        return result;
//    }

    /**
     * 查询线下处方 入库操作
     *
     * @param organId
     * @param patientDTO
     * @param timeQuantum
     * @param flag
     */
    @RpcService
    public List<HisRecipe> queryHisRecipeInfo(Integer organId, PatientDTO patientDTO, Integer timeQuantum, Integer flag) {
        //查询数据
        HisResponseTO<List<QueryHisRecipResTO>> responseTO = queryData(organId,patientDTO,timeQuantum,flag);
        if (null == responseTO || null == responseTO.getData()) {
            return null;
        }
        if(responseTO.getData()==null){
            return null;
        }
        try {
            /** 更新数据校验*/
            hisRecipeInfoCheck(responseTO.getData(), patientDTO);
        } catch (Exception e) {
            LOGGER.error("queryHisRecipeInfo hisRecipeInfoCheck error ", e);
        }
        List<HisRecipe> recipes=new ArrayList<>();
        try {
            //数据入库
            recipes=saveHisRecipeInfo(responseTO, patientDTO, flag);
        } catch (Exception e) {
            LOGGER.error("queryHisRecipeInfo saveHisRecipeInfo error ", e);
        }
        return recipes;
    }

    /**
     *
     * @param organId
     * @param patientDTO
     * @param timeQuantum
     * @param flag
     * @return
     * @Author liumin
     * @Desciption  从 his查询待缴费已缴费的处方信息
     */
    public HisResponseTO<List<QueryHisRecipResTO>> queryData(Integer organId, PatientDTO patientDTO, Integer timeQuantum, Integer flag) {
        //TODO question 查询条件带recipeCode
        //TODO question 让前置机去过滤数据
        PatientBaseInfo patientBaseInfo = new PatientBaseInfo();
        patientBaseInfo.setBirthday(patientDTO.getBirthday());
        patientBaseInfo.setPatientName(patientDTO.getPatientName());
        patientBaseInfo.setPatientSex(patientDTO.getPatientSex());
        patientBaseInfo.setMobile(patientDTO.getMobile());
        patientBaseInfo.setMpi(patientDTO.getMpiId());
        patientBaseInfo.setCardID(patientDTO.getCardId());
        patientBaseInfo.setCertificate("230103198006264247");
        //测试数据
//        patientBaseInfo.setMpi("2c90820c76cceb7b0176fec2bee7688d");
//        patientBaseInfo.setCertificate("230103198006264247");
//        patientBaseInfo.setPatientName("刘魏娜");
//        patientBaseInfo.setPatientSex("2");
        //patientBaseInfo.setBirthday("1980-06-26 00:00:00");

        QueryRecipeRequestTO queryRecipeRequestTO = new QueryRecipeRequestTO();
        queryRecipeRequestTO.setPatientInfo(patientBaseInfo);
        queryRecipeRequestTO.setStartDate(tranDateByFlagNew(timeQuantum.toString()));
        queryRecipeRequestTO.setEndDate(new Date());
        queryRecipeRequestTO.setOrgan(organId);
        queryRecipeRequestTO.setQueryType(flag);

        IRecipeHisService recipeHisService = AppContextHolder.getBean("his.iRecipeHisService", IRecipeHisService.class);
        LOGGER.info("queryHisRecipeInfo input:" + JSONUtils.toString(queryRecipeRequestTO, QueryRecipeRequestTO.class));
        HisResponseTO<List<QueryHisRecipResTO>> responseTO = recipeHisService.queryHisRecipeInfo(queryRecipeRequestTO);
        LOGGER.info("queryHisRecipeInfo output:" + JSONUtils.toString(responseTO, HisResponseTO.class));
        //TODO 注释
//        if(responseTO!=null){
//            List<QueryHisRecipResTO> queryHisRecipResTOs=responseTO.getData();
//            if(!CollectionUtils.isEmpty(queryHisRecipResTOs)){
//                for(QueryHisRecipResTO queryHisRecipResTO:queryHisRecipResTOs){
////                    if(1==queryHisRecipResTO.getStatus()){
////                        queryHisRecipResTO.setRecipeCode("444");
////                    }
////                    if(2==queryHisRecipResTO.getStatus()){
////                        queryHisRecipResTO.setRecipeCode("111");
////                        queryHisRecipResTO.setStatus(1);
////                    }
//                    queryHisRecipResTO.setRecipeCostNumber("111");
//                    queryHisRecipResTO.setDecoctionFee(new BigDecimal(20));
//                    queryHisRecipResTO.setTcmFee(new BigDecimal(19));
//                }
//            }
//        }
        //过滤数据
        responseTO=filterData(responseTO);
        LOGGER.info("queryHisRecipeInfo queryData:{}.", JSONUtils.toString(responseTO));
        return responseTO;
    }

    /**
     *
     * @param responseTO
     * @author liumin
     * @Description 数据过滤
     * @return
     */
    private HisResponseTO<List<QueryHisRecipResTO>> filterData(HisResponseTO<List<QueryHisRecipResTO>> responseTO) {
        if(!StringUtils.isEmpty(recipeCodeThreadLocal.get())){
            String recipeCode=recipeCodeThreadLocal.get();
            if(responseTO!=null){
                LOGGER.info("queryHisRecipeInfo recipeCodeThreadLocal:{}",recipeCode);
                List<QueryHisRecipResTO> queryHisRecipResTOs=responseTO.getData();
                List<QueryHisRecipResTO> queryHisRecipResTOFilters=new ArrayList<>();
                if(!CollectionUtils.isEmpty(queryHisRecipResTOs)){
                    for(QueryHisRecipResTO queryHisRecipResTO:queryHisRecipResTOs){
                        if(recipeCode.equals(queryHisRecipResTO.getRecipeCode())){
                            queryHisRecipResTOFilters.add(queryHisRecipResTO);
                        }
                    }
                }
                responseTO.setData(queryHisRecipResTOFilters);
            }
        }
        return responseTO;
    }

    /**
     * 线下处方转换成前端所需对象
     * @param responseTO
     * @param patientDTO
     * @param flag
     * @return
     */
    public List<HisRecipeVO> covertToHisRecipeObject(HisResponseTO<List<QueryHisRecipResTO>> responseTO, PatientDTO patientDTO, Integer flag) {
        List<HisRecipeVO> hisRecipeVOs=new ArrayList<>();
        if(responseTO==null){
            return hisRecipeVOs;
        }
        List<QueryHisRecipResTO> queryHisRecipResTOList = responseTO.getData();
        if(CollectionUtils.isEmpty(queryHisRecipResTOList)){
            return hisRecipeVOs;
        }
        LOGGER.info("covertHisRecipeObject queryHisRecipResTOList:" + JSONUtils.toString(queryHisRecipResTOList));
        for (QueryHisRecipResTO queryHisRecipResTO : queryHisRecipResTOList) {
            HisRecipe hisRecipe1 = hisRecipeDAO.getHisRecipeBMpiIdyRecipeCodeAndClinicOrgan(
                    patientDTO.getMpiId(), queryHisRecipResTO.getClinicOrgan(), queryHisRecipResTO.getRecipeCode());
            //数据库不存在处方信息，则新增
            if (null == hisRecipe1) {
                HisRecipe hisRecipe = new HisRecipe();
                hisRecipe.setCertificate(patientDTO.getCertificate());
                hisRecipe.setCertificateType(patientDTO.getCertificateType());
                hisRecipe.setMpiId(patientDTO.getMpiId());
                hisRecipe.setPatientName(patientDTO.getPatientName());
                hisRecipe.setPatientAddress(patientDTO.getAddress());
                hisRecipe.setPatientNumber(queryHisRecipResTO.getPatientNumber());
                hisRecipe.setPatientTel(patientDTO.getMobile());
                hisRecipe.setRegisteredId(queryHisRecipResTO.getRegisteredId());
                hisRecipe.setRecipeCode(queryHisRecipResTO.getRecipeCode());
                hisRecipe.setDepartCode(queryHisRecipResTO.getDepartCode());
                hisRecipe.setDepartName(queryHisRecipResTO.getDepartName());
                hisRecipe.setDoctorName(queryHisRecipResTO.getDoctorName());
                hisRecipe.setCreateDate(queryHisRecipResTO.getCreateDate());
                hisRecipe.setStatus(queryHisRecipResTO.getStatus());
                if(new Integer(2).equals(queryHisRecipResTO.getMedicalType())){
                    hisRecipe.setMedicalType(queryHisRecipResTO.getMedicalType());//医保类型
                }else{
                    hisRecipe.setMedicalType(1);//默认自费
                }
                hisRecipe.setRecipeFee(queryHisRecipResTO.getRecipeFee());
                hisRecipe.setRecipeType(queryHisRecipResTO.getRecipeType());
                hisRecipe.setClinicOrgan(queryHisRecipResTO.getClinicOrgan());
                hisRecipe.setCreateTime(new Date());
                hisRecipe.setExtensionFlag(1);
                if (queryHisRecipResTO.getExtensionFlag() == null) {
                    hisRecipe.setRecipePayType(0); //设置外延处方的标志
                } else {
                    hisRecipe.setRecipePayType(queryHisRecipResTO.getExtensionFlag()); //设置外延处方的标志
                }

                if(!StringUtils.isEmpty(queryHisRecipResTO.getDiseaseName())){
                    hisRecipe.setDiseaseName(queryHisRecipResTO.getDiseaseName());
                }else {
                    hisRecipe.setDiseaseName("无");
                }
                hisRecipe.setDisease(queryHisRecipResTO.getDisease());
                if(!StringUtils.isEmpty(queryHisRecipResTO.getDoctorCode())){
                    hisRecipe.setDoctorCode(queryHisRecipResTO.getDoctorCode());
                }
                OrganService organService = BasicAPI.getService(OrganService.class);
                OrganDTO organDTO = organService.getByOrganId(queryHisRecipResTO.getClinicOrgan());
                if(null !=organDTO) {
                    hisRecipe.setOrganName(organDTO.getName());
                }
                if (null != queryHisRecipResTO.getMedicalInfo()) {
                    MedicalInfo medicalInfo = queryHisRecipResTO.getMedicalInfo();
                    if(!ObjectUtils.isEmpty(medicalInfo.getMedicalAmount())){
                        hisRecipe.setMedicalAmount(medicalInfo.getMedicalAmount());
                    }
                    if(!ObjectUtils.isEmpty(medicalInfo.getCashAmount())){
                        hisRecipe.setCashAmount(medicalInfo.getCashAmount());
                    }
                    if(!ObjectUtils.isEmpty(medicalInfo.getTotalAmount())){
                        hisRecipe.setTotalAmount(medicalInfo.getTotalAmount());
                    }
                }
                hisRecipe.setGiveMode(queryHisRecipResTO.getGiveMode());
                hisRecipe.setDeliveryCode(queryHisRecipResTO.getDeliveryCode());
                hisRecipe.setDeliveryName(queryHisRecipResTO.getDeliveryName());
                hisRecipe.setSendAddr(queryHisRecipResTO.getSendAddr());
                hisRecipe.setRecipeSource(queryHisRecipResTO.getRecipeSource());
                hisRecipe.setReceiverName(queryHisRecipResTO.getReceiverName());
                hisRecipe.setReceiverTel(queryHisRecipResTO.getReceiverTel());
                //未缓存在平台
                hisRecipe.setIsCachePlatform(0);
                HisRecipeVO hisRecipeVO = ObjectCopyUtils.convert(hisRecipe, HisRecipeVO.class);
                //设置其它信息
                hisRecipeVO.setOrganDiseaseName(hisRecipe.getDiseaseName());
                setOtherInfo(hisRecipeVO,hisRecipe.getMpiId(),queryHisRecipResTO.getRecipeCode(), queryHisRecipResTO.getClinicOrgan());

                if (null != queryHisRecipResTO.getDrugList()) {
                    List<HisRecipeDetailVO> hisRecipeDetailVOs=new ArrayList<>();
                    for (RecipeDetailTO recipeDetailTO : queryHisRecipResTO.getDrugList()) {
                        HisRecipeDetail detail = ObjectCopyUtils.convert(recipeDetailTO, HisRecipeDetail.class);
                        detail.setDrugName(recipeDetailTO.getDrugName());
                        detail.setDrugSpec(recipeDetailTO.getDrugSpec());
                        detail.setDrugUnit(recipeDetailTO.getDrugUnit());
                        detail.setPack(recipeDetailTO.getPack());
                        detail.setUseTotalDose(recipeDetailTO.getUseTotalDose());
                        HisRecipeDetailVO hisRecipeDetailVO = ObjectCopyUtils.convert(detail, HisRecipeDetailVO.class);
                        hisRecipeDetailVOs.add(hisRecipeDetailVO);
                    }
                    hisRecipeVO.setRecipeDetail(hisRecipeDetailVOs);
                }
                hisRecipeVOs.add(hisRecipeVO);
            }else{
                //如果为已支付，不予返回
                if(!new Integer("2").equals(hisRecipe1.getStatus())){
                    HisRecipeVO hisRecipeVO = ObjectCopyUtils.convert(hisRecipe1, HisRecipeVO.class);
                    setOtherInfo(hisRecipeVO,hisRecipe1.getMpiId(),queryHisRecipResTO.getRecipeCode(), queryHisRecipResTO.getClinicOrgan());
                    hisRecipeVO.setOrganDiseaseName(queryHisRecipResTO.getDiseaseName());
                    if (null != queryHisRecipResTO.getDrugList()) {
                        List<HisRecipeDetailVO> hisRecipeDetailVOs=new ArrayList<>();
                        for (RecipeDetailTO recipeDetailTO : queryHisRecipResTO.getDrugList()) {
                            HisRecipeDetail detail = ObjectCopyUtils.convert(recipeDetailTO, HisRecipeDetail.class);
                            detail.setDrugName(recipeDetailTO.getDrugName());
                            detail.setDrugSpec(recipeDetailTO.getDrugSpec());
                            detail.setDrugUnit(recipeDetailTO.getDrugUnit());
                            detail.setPack(recipeDetailTO.getPack());
                            detail.setUseTotalDose(recipeDetailTO.getUseTotalDose());
                            HisRecipeDetailVO hisRecipeDetailVO = ObjectCopyUtils.convert(detail, HisRecipeDetailVO.class);
                            hisRecipeDetailVOs.add(hisRecipeDetailVO);
                        }
                        hisRecipeVO.setRecipeDetail(hisRecipeDetailVOs);
                    }
                    hisRecipeVOs.add(hisRecipeVO);
                }
            }

        }
        return hisRecipeVOs;
    }

    private void setOtherInfo(HisRecipeVO hisRecipeVO,String mpiId, String recipeCode, Integer clinicOrgan) {
        Recipe recipe = recipeDAO.getByHisRecipeCodeAndClinicOrganAndMpiid(mpiId,recipeCode, clinicOrgan);
        if (recipe == null) {
            hisRecipeVO.setOrderStatusText("待支付");
            hisRecipeVO.setFromFlag(1);
            hisRecipeVO.setJumpPageType(0);
        } else {
            RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
            EmrRecipeManager.getMedicalInfo(recipe, recipeExtend);
                if (recipeExtend != null && recipeExtend.getFromFlag() == 0) {
                    //表示该处方来源于HIS
                    if(StringUtils.isEmpty(recipe.getOrderCode())){
                        hisRecipeVO.setOrderStatusText("待支付");
                        hisRecipeVO.setJumpPageType(0);
                    }else{
                        RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
                        if(recipeOrder!=null){
                            if(new Integer(0).equals(recipeOrder.getPayFlag())){
                                hisRecipeVO.setOrderStatusText("待支付");
                            }else{
                                hisRecipeVO.setOrderStatusText("已完成");
                            }
                            hisRecipeVO.setJumpPageType(1);//跳转到订单详情页
                            hisRecipeVO.setStatusText(getTipsByStatusForPatient(recipe, recipeOrder));
                            hisRecipeVO.setOrderCode(recipe.getOrderCode());
                        }
                    }
                    hisRecipeVO.setFromFlag(recipe.getRecipeSourceType()==2?1:0);
                } else {
                    //表示该处方来源于平台
                    hisRecipeVO.setOrderStatusText("待支付");
                    hisRecipeVO.setFromFlag(0);
                    hisRecipeVO.setJumpPageType(0);
                    hisRecipeVO.setOrganDiseaseName(recipe.getOrganDiseaseName());
                    hisRecipeVO.setHisRecipeID(recipe.getRecipeId());
                    List<HisRecipeDetailVO> recipeDetailVOS = getHisRecipeDetailVOS(recipe);
                    hisRecipeVO.setRecipeDetail(recipeDetailVOS);
                }
            }
    }


    /**
     * 保存线下处方数据到cdr_his_recipe、HisRecipeDetail、HisRecipeExt
     * 保存前先校验数据
     * （1）如果数据在cdr_his_recipe已经存在,并且不是由本人生成，支付状态为未支付，先删除cdr_his_recipe[这里少删了表]，后新增
     *                                  ，并且是由本人生成 ，则跳过此处方
     * （2）如果数据在cdr_his_recipe不存在，则直接新增
     * @param responseTO
     * @param patientDTO
     * @param flag
     * @return
     */
    @RpcService
    public List<HisRecipe> saveHisRecipeInfo(HisResponseTO<List<QueryHisRecipResTO>> responseTO, PatientDTO patientDTO, Integer flag) {
        List<HisRecipe> hisRecipes=new ArrayList<>();
        if (responseTO == null) {
            return hisRecipes;
        }
        List<QueryHisRecipResTO> queryHisRecipResTOList = responseTO.getData();

        if(CollectionUtils.isEmpty(queryHisRecipResTOList)){
            return hisRecipes;
        }
        LOGGER.info("saveHisRecipeInfo queryHisRecipResTOList:" + JSONUtils.toString(queryHisRecipResTOList));
        for (QueryHisRecipResTO queryHisRecipResTO : queryHisRecipResTOList) {
//            HisRecipe hisRecipe2 = hisRecipeDAO.getHisRecipeBMpiIdyRecipeCodeAndClinicOrgan(
//                    patientDTO.getMpiId(), queryHisRecipResTO.getClinicOrgan(), queryHisRecipResTO.getRecipeCode());
            HisRecipe hisRecipe1 = hisRecipeDAO.getHisRecipeByRecipeCodeAndClinicOrgan(queryHisRecipResTO.getClinicOrgan(), queryHisRecipResTO.getRecipeCode());
            if(hisRecipe1!=null){
                if(!patientDTO.getMpiId().equals(hisRecipe1.getMpiId())){
                    List<Integer> hisRecipeIds=new ArrayList<>();
                    hisRecipeIds.add(hisRecipe1.getHisRecipeID());
                    //同recipeCode,organId不是本人生成的线下处方
                    Recipe haveRecipe = recipeDAO.getByHisRecipeCodeAndClinicOrgan(queryHisRecipResTO.getRecipeCode(), queryHisRecipResTO.getClinicOrgan());
                    //如果处方已经转到cdr_recipe表并且支付状态为待支付并且非本人转储到cdr_his_recipe，则先删除后新增
                    if (haveRecipe != null) {
                        if(new Integer(0).equals(haveRecipe.getPayFlag())){
                            hisRecipeDAO.deleteByHisRecipeIds(hisRecipeIds);
                            hisRecipe1=null;
                        }
                    }else{
                        hisRecipeDAO.deleteByHisRecipeIds(hisRecipeIds);
                        hisRecipe1=null;
                    }
                }else{
                    //本人
                    hisRecipes.add(hisRecipe1);
                    //如果已缴费处方在数据库里已存在，且数据里的状态是未缴费，则将数据库里的未缴费状态更新为已缴费状态
                    if(2 == flag){
                        if(1 == hisRecipe1.getStatus()){
                            hisRecipe1.setStatus(queryHisRecipResTO.getStatus());
                            hisRecipeDAO.update(hisRecipe1);
                        }
                    }
                }
            }

            //数据库不存在处方信息，则新增
            if (null == hisRecipe1) {
                HisRecipe hisRecipe = new HisRecipe();
                hisRecipe.setCertificate(patientDTO.getCertificate());
                hisRecipe.setCertificateType(patientDTO.getCertificateType());
                hisRecipe.setMpiId(patientDTO.getMpiId());
                hisRecipe.setPatientName(patientDTO.getPatientName());
                hisRecipe.setPatientAddress(patientDTO.getAddress());
                hisRecipe.setPatientNumber(queryHisRecipResTO.getPatientNumber());
                hisRecipe.setPatientTel(patientDTO.getMobile());
                hisRecipe.setRegisteredId(queryHisRecipResTO.getRegisteredId());
                hisRecipe.setRecipeCode(queryHisRecipResTO.getRecipeCode());
                hisRecipe.setDepartCode(queryHisRecipResTO.getDepartCode());
                hisRecipe.setDepartName(queryHisRecipResTO.getDepartName());
                hisRecipe.setDoctorName(queryHisRecipResTO.getDoctorName());
                hisRecipe.setCreateDate(queryHisRecipResTO.getCreateDate());
                hisRecipe.setStatus(queryHisRecipResTO.getStatus());
                if(new Integer(2).equals(queryHisRecipResTO.getMedicalType())){
                    hisRecipe.setMedicalType(queryHisRecipResTO.getMedicalType());//医保类型
                }else{
                    hisRecipe.setMedicalType(1);//默认自费
                }
                hisRecipe.setRecipeFee(queryHisRecipResTO.getRecipeFee());
                hisRecipe.setRecipeType(queryHisRecipResTO.getRecipeType());
                hisRecipe.setClinicOrgan(queryHisRecipResTO.getClinicOrgan());
                hisRecipe.setCreateTime(new Date());
                hisRecipe.setExtensionFlag(1);
                if (queryHisRecipResTO.getExtensionFlag() == null) {
                    hisRecipe.setRecipePayType(0); //设置外延处方的标志
                } else {
                    hisRecipe.setRecipePayType(queryHisRecipResTO.getExtensionFlag()); //设置外延处方的标志
                }

                if(!StringUtils.isEmpty(queryHisRecipResTO.getDiseaseName())){
                    hisRecipe.setDiseaseName(queryHisRecipResTO.getDiseaseName());
                }else {
                    hisRecipe.setDiseaseName("无");
                }
                hisRecipe.setDisease(queryHisRecipResTO.getDisease());
                if(!StringUtils.isEmpty(queryHisRecipResTO.getDoctorCode())){
                    hisRecipe.setDoctorCode(queryHisRecipResTO.getDoctorCode());
                }
                OrganService organService = BasicAPI.getService(OrganService.class);
                OrganDTO organDTO = organService.getByOrganId(queryHisRecipResTO.getClinicOrgan());
                if(null !=organDTO) {
                    hisRecipe.setOrganName(organDTO.getName());
                }
                if (null != queryHisRecipResTO.getMedicalInfo()) {
                    MedicalInfo medicalInfo = queryHisRecipResTO.getMedicalInfo();
                    if(!ObjectUtils.isEmpty(medicalInfo.getMedicalAmount())){
                        hisRecipe.setMedicalAmount(medicalInfo.getMedicalAmount());
                    }
                    if(!ObjectUtils.isEmpty(medicalInfo.getCashAmount())){
                        hisRecipe.setCashAmount(medicalInfo.getCashAmount());
                    }
                    if(!ObjectUtils.isEmpty(medicalInfo.getTotalAmount())){
                        hisRecipe.setTotalAmount(medicalInfo.getTotalAmount());
                    }
                }
                hisRecipe.setGiveMode(queryHisRecipResTO.getGiveMode());
                hisRecipe.setDeliveryCode(queryHisRecipResTO.getDeliveryCode());
                hisRecipe.setDeliveryName(queryHisRecipResTO.getDeliveryName());
                hisRecipe.setSendAddr(queryHisRecipResTO.getSendAddr());
                hisRecipe.setRecipeSource(queryHisRecipResTO.getRecipeSource());
                hisRecipe.setReceiverName(queryHisRecipResTO.getReceiverName());
                hisRecipe.setReceiverTel(queryHisRecipResTO.getReceiverTel());

                //中药
                hisRecipe.setRecipeCostNumber(queryHisRecipResTO.getRecipeCostNumber());
                hisRecipe.setTcmFee(queryHisRecipResTO.getTcmFee());
                hisRecipe.setDecoctionFee(queryHisRecipResTO.getDecoctionFee());
                hisRecipe.setDecoctionCode(queryHisRecipResTO.getDecoctionCode());
                hisRecipe.setDecoctionText(queryHisRecipResTO.getDecoctionText());
                hisRecipe.setTcmNum(queryHisRecipResTO.getTcmNum()==null?null:String.valueOf(queryHisRecipResTO.getTcmNum()));
                try {
                    hisRecipe = hisRecipeDAO.save(hisRecipe);
                    LOGGER.info("saveHisRecipeInfo hisRecipe:{} 当前时间：{}",hisRecipe, System.currentTimeMillis());
                    hisRecipes.add(hisRecipe);
                } catch (Exception e) {
                    LOGGER.error("hisRecipeDAO.save error ", e);
                    return hisRecipes;
                }

                //TODO 需要提到判断条件之外 保存前判断处方关联数据是否存在  存在先删除 然后新增 不存在新增
                if (null != queryHisRecipResTO.getExt()) {
                    for (ExtInfoTO extInfoTO : queryHisRecipResTO.getExt()) {
                        HisRecipeExt ext = ObjectCopyUtils.convert(extInfoTO, HisRecipeExt.class);
                        ext.setHisRecipeId(hisRecipe.getHisRecipeID());
                        hisRecipeExtDAO.save(ext);
                    }
                }
                //TODO 需要提到判断条件之外 保存前判断处方关联数据是否存在  存在先删除 然后新增 不存在新增
                if (null != queryHisRecipResTO.getDrugList()) {
                    for (RecipeDetailTO recipeDetailTO : queryHisRecipResTO.getDrugList()) {
                        HisRecipeDetail detail = ObjectCopyUtils.convert(recipeDetailTO, HisRecipeDetail.class);
                        detail.setHisRecipeId(hisRecipe.getHisRecipeID());
                        detail.setRecipeDeatilCode(recipeDetailTO.getRecipeDeatilCode());
                        detail.setDrugName(recipeDetailTO.getDrugName());
                        detail.setPrice(recipeDetailTO.getPrice());
                        detail.setTotalPrice(recipeDetailTO.getTotalPrice());
                        detail.setUsingRate(recipeDetailTO.getUsingRate());
                        detail.setUsePathways(recipeDetailTO.getUsePathWays());
                        detail.setDrugSpec(recipeDetailTO.getDrugSpec());
                        detail.setDrugUnit(recipeDetailTO.getDrugUnit());
                        //date 20200526
                        //修改线下处方同步用药天数，判断是否有小数类型的用药天数
                        //修改逻辑，UseDays这个字段为老字段只有整数
                        //UseDaysB这个字段为字符类型，可能小数，准备之后用药天数都用这个字段
                        //取对应没有的字段设置传过来的值
                        //这两个值只会有一个没有传
                        if(null != recipeDetailTO.getUseDays()){
                            //设置字符类型的
                            detail.setUseDaysB(recipeDetailTO.getUseDays().toString());
                        }
                        if(null != recipeDetailTO.getUseDaysB()){
                            //设置int类型的
                            //设置字符转向上取整
                            int useDays = new BigDecimal(recipeDetailTO.getUseDaysB()).setScale(0, BigDecimal.ROUND_UP).intValue();
                            detail.setUseDays(useDays);
                        }
//                        detail.setUseDays(recipeDetailTO.getUseDays());
//                        detail.setUseDaysB(recipeDetailTO.getUseDays().toString());
                        detail.setDrugCode(recipeDetailTO.getDrugCode());
                        detail.setUsingRateText(recipeDetailTO.getUsingRateText());
                        detail.setUsePathwaysText(recipeDetailTO.getUsePathwaysText());
                        //  线下特殊用法
                        detail.setUseDoseStr(recipeDetailTO.getUseDoseStr());
                        detail.setUseDose(recipeDetailTO.getUseDose());
                        detail.setUseDoseUnit(recipeDetailTO.getUseDoseUnit());
                        detail.setSaleName(recipeDetailTO.getSaleName());
                        detail.setPack(recipeDetailTO.getPack());
                        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
                        if (StringUtils.isNotEmpty(detail.getRecipeDeatilCode())) {
                            List<OrganDrugList> organDrugLists = organDrugListDAO.findByOrganIdAndDrugCodes(hisRecipe.getClinicOrgan(), Arrays.asList(detail.getDrugCode()));
                            if (CollectionUtils.isEmpty(organDrugLists)) {
                                LOGGER.info("saveHisRecipeInfo organDrugLists his传过来的药品编码没有在对应机构维护,organId:"+hisRecipe.getClinicOrgan()+",organDrugCode:" + detail.getDrugCode());
                            }
                        }
                        detail.setStatus(1);
                        hisRecipeDetailDAO.save(detail);
                    }
                }
            }
        }
        return hisRecipes;
    }

    /**
     * @param flag 根据flag转化日期 查询标志 0-近一个月数据;1-近三个月;2-近半年;3-近一年
     *             1 代表一个月  3 代表三个月 6 代表6个月
     * @return
     */
    private Date tranDateByFlagNew(String flag) {
        Date beginTime = new Date();
        Calendar ca = Calendar.getInstance();
        //得到当前日期
        ca.setTime(new Date());
        if ("6".equals(flag)) {  //近半年数据
            ca.add(Calendar.MONTH, -6);//月份减6
            Date resultDate = ca.getTime(); //结果
            beginTime = resultDate;
        } else if ("3".equals(flag)) {  //近三个月数据
            ca.add(Calendar.MONTH, -3);//月份减3
            Date resultDate = ca.getTime(); //结果
            beginTime = resultDate;
        } else if ("1".equals(flag)) { //近一个月数据
            ca.add(Calendar.MONTH, -1);//月份减1
            Date resultDate = ca.getTime(); //结果
            beginTime = resultDate;
        }
        return beginTime;
    }

    private List<HisRecipeDetailVO> getHisRecipeDetailVOS(Recipe recipe) {
        List<HisRecipeDetailVO> recipeDetailVOS = new ArrayList<>();
        List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipe.getRecipeId());
        for (Recipedetail recipedetail : recipedetails) {
            HisRecipeDetailVO hisRecipeDetailVO = new HisRecipeDetailVO();
            hisRecipeDetailVO.setDrugName(recipedetail.getDrugName());
            hisRecipeDetailVO.setDrugSpec(recipedetail.getDrugSpec());
            hisRecipeDetailVO.setDrugUnit(recipedetail.getDrugUnit());
            hisRecipeDetailVO.setPack(recipedetail.getPack());
            hisRecipeDetailVO.setDrugForm(recipedetail.getDrugForm());
            hisRecipeDetailVO.setUseTotalDose(new BigDecimal(recipedetail.getUseTotalDose()));
            hisRecipeDetailVO.setUseDose(recipedetail.getUseDose()==null?"":recipedetail.getUseDose().toString());
            hisRecipeDetailVO.setDrugUnit(recipedetail.getDrugUnit());
            recipeDetailVOS.add(hisRecipeDetailVO);
        }
        return recipeDetailVOS;
    }


    /**
     * @author liumin
     * @Description 获取处方详情
     */
    @RpcService
    public Map<String, Object> getHisRecipeDetail(Integer hisRecipeId,String mpiId,String recipeCode,String organId,Integer isCachePlatform, String cardId){
        //是否缓存标志是必传字段
        //如果传1：转平台处方并根据hisRecipeId去表里查返回详情
        //如果传0:根据mpiid+机构+recipeCode去his查 并缓存到cdr_his_recipe 然后转平台处方并根据hisRecipeId去表里查返回详情
        HisRecipe hisRecipe = hisRecipeDAO.getHisRecipeBMpiIdyRecipeCodeAndClinicOrgan(mpiId, Integer.parseInt(organId), recipeCode);
        if (hisRecipe == null) {
            throw new DAOException(700, "该处方单信息已变更，请退出重新获取处方信息。");
        }
        LOGGER.info("getHisRecipeDetail hisRecipe:{}.", JSONUtils.toString(hisRecipe));
        //待处理
        Recipe recipe = recipeDAO.getByRecipeCodeAndClinicOrgan(recipeCode, Integer.parseInt(organId));
        Integer payFlag = 0;
        if (recipe != null && StringUtils.isNotEmpty(recipe.getOrderCode())) {
            RecipeOrder recipeOrder = recipeOrderDAO.getByOrderCode(recipe.getOrderCode());
            if (new Integer(1).equals(recipeOrder.getPayFlag())) {
                payFlag = 1;
            }
        }
        //TODO liumin 这个条件应改为hisRecipeId是否存在，存在表明已经转成线上了，不用走这个逻辑了  但是重复走这个逻辑也没关系，保存时会判断，存在数据也不会新增
        if(hisRecipe.getStatus() != 2 || payFlag == 1){
            LOGGER.info("getHisRecipeDetail 进入");
            try{
                PatientService patientService = BasicAPI.getService(PatientService.class);
                PatientDTO patientDTO = patientService.getPatientBeanByMpiId(mpiId);
                if (null == patientDTO) {
                    throw new DAOException(609, "患者信息不存在");
                }
                if (StringUtils.isNotEmpty(cardId)) {
                    patientDTO.setCardId(cardId);
                } else {
                    patientDTO.setCardId("");
                }
                recipeCodeThreadLocal.set(recipeCode);
                //线下处方处理(存储到cdr_his相关表)
                List<HisRecipe> hisRecipes = queryHisRecipeInfo(new Integer(organId), patientDTO, 180, 1);
                if(CollectionUtils.isEmpty(hisRecipes)){
                    return initReturnMap();
                }else{
                    hisRecipeId=hisRecipes.get(0).getHisRecipeID();
                }
            }catch(Exception e){
                LOGGER.error("getHisRecipeDetail error hisRecipeId:{}",hisRecipeId,e);
            }finally {
                recipeCodeThreadLocal.remove();
            }
        }
        //存储到recipe相关表
        if(hisRecipeId==null){
            throw new DAOException(DAOException.VALUE_NEEDED, "hisRecipeId不能为空！");
        }
        return getHisRecipeDetailByHisRecipeId(hisRecipeId);

    }

    private Map<String, Object> initReturnMap() {
        Map<String ,Object> map=new HashMap<>();
        map.put("hisRecipeDetails", null);
        map.put("hisRecipeExts", null);
        map.put("showText", null);
        return map;
    }

    /**
     * @param hisRecipeId
     * @author liumin
     * @Description 转平台处方并根据hisRecipeId去表里查返回详情
     * @return
     */
    private Map<String, Object> getHisRecipeDetailByHisRecipeId(Integer hisRecipeId) {
        if(hisRecipeId==null){
            return null;
        }
        //将线下处方转化成线上处方
        HisRecipe hisRecipe = hisRecipeDAO.get(hisRecipeId);
        if (hisRecipe == null) {
            throw new DAOException(DAOException.DAO_NOT_FOUND, "没有查询到来自医院的处方单");
        }
        Recipe recipe = saveRecipeFromHisRecipe(hisRecipe);
        if (recipe != null) {
            saveRecipeExt(recipe, hisRecipe);
            //生成处方详情
            savaRecipeDetail(recipe.getRecipeId(),hisRecipe);
        }
        RecipeService recipeService = ApplicationUtils.getRecipeService(RecipeService.class);
        Map<String,Object> map = recipeService.getPatientRecipeById(recipe.getRecipeId());
        //if(recipeListService.isReturnRecipeDetail(recipe.getClinicOrgan(),recipe.getRecipeType(),recipe.getPayFlag())){
        List<HisRecipeDetail> hisRecipeDetails = hisRecipeDetailDAO.findByHisRecipeId(hisRecipeId);
        map.put("hisRecipeDetails", hisRecipeDetails);
        //}
        List<HisRecipeExt> hisRecipeExts = hisRecipeExtDAO.findByHisRecipeId(hisRecipeId);
        map.put("hisRecipeExts", hisRecipeExts);
        map.put("showText", hisRecipe.getShowText());
        return map;
    }

    private void saveRecipeExt(Recipe recipe, HisRecipe hisRecipe) {
        Integer recipeId = recipe.getRecipeId();
        RecipeExtend haveRecipeExt = recipeExtendDAO.getByRecipeId(recipeId);
        if (haveRecipeExt != null) {
            return;
        }
        RecipeExtend recipeExtend = new RecipeExtend();
        recipeExtend.setRecipeId(recipeId);
        recipeExtend.setFromFlag(0);
        recipeExtend.setRegisterID(hisRecipe.getRegisteredId());
        try {
            IRevisitExService exService = RevisitAPI.getService(IRevisitExService.class);
            RevisitExDTO consultExDTO = exService.getByRegisterId(hisRecipe.getRegisteredId());
            if (consultExDTO != null) {
                recipeExtend.setCardNo(consultExDTO.getCardId());
            }
        } catch (Exception e) {
            LOGGER.error("线下处方转线上通过挂号序号关联复诊 error", e);
        }
        //中药
        recipeExtend.setRecipeCostNumber(hisRecipe.getRecipeCostNumber());
        RecipeBean recipeBean = new RecipeBean();
        BeanUtils.copy(recipe, recipeBean);
        emrRecipeManager.saveMedicalInfo(recipeBean, recipeExtend);
        recipeExtendDAO.save(recipeExtend);
    }

    private Recipe saveRecipeFromHisRecipe(HisRecipe hisRecipe) {
        LOGGER.info("saveRecipeFromHisRecipe hisRecipe:{}.", JSONUtils.toString(hisRecipe));
        Recipe haveRecipe = recipeDAO.getByHisRecipeCodeAndClinicOrgan(hisRecipe.getRecipeCode(), hisRecipe.getClinicOrgan());
        LOGGER.info("saveRecipeFromHisRecipe haveRecipe:{}.", JSONUtils.toString(haveRecipe));
        if (haveRecipe != null) {
            //TODO 在表存在 更新除recipeId外所有数据 这里删掉 因为在校验的时候会判断如果不是由本人生成的待缴费处方会更新全部信息
            //如果处方已经转到cdr_recipe表并且支付状态为待支付并且非本人转储到cdr_recipe，则替换用户信息
            if(new Integer(0).equals(haveRecipe.getPayFlag())
                    &&!StringUtils.isEmpty(hisRecipe.getMpiId())
                    &&!hisRecipe.getMpiId().equals(haveRecipe.getMpiid())){
                //修改处方患者信息
                haveRecipe.setMpiid(hisRecipe.getMpiId());
                haveRecipe.setPatientName(hisRecipe.getPatientName());
                haveRecipe.setPatientID(hisRecipe.getPatientNumber());
                recipeDAO.update(haveRecipe);
            }
            return haveRecipe;
        }
        Recipe recipe = new Recipe();
        recipe.setBussSource(0);
        //通过挂号序号关联复诊
        try {
            IRevisitExService exService = RevisitAPI.getService(IRevisitExService.class);
            RevisitExDTO consultExDTO = exService.getByRegisterId(hisRecipe.getRegisteredId());
            if (consultExDTO != null){
                recipe.setBussSource(2);
                recipe.setClinicId(consultExDTO.getConsultId());
            }
        }catch (Exception e){
            LOGGER.error("线下处方转线上通过挂号序号关联复诊 error",e);
        }

        recipe.setClinicOrgan(hisRecipe.getClinicOrgan());
        recipe.setMpiid(hisRecipe.getMpiId());
        recipe.setPatientName(hisRecipe.getPatientName());
        recipe.setPatientID(hisRecipe.getPatientNumber());
        recipe.setPatientStatus(1);
        recipe.setOrganName(hisRecipe.getOrganName());
        recipe.setRecipeCode(hisRecipe.getRecipeCode());
        recipe.setRecipeType(hisRecipe.getRecipeType());
        //BUG#50592 【实施】【上海市奉贤区中心医院】【A】查询线下处方缴费提示系统繁忙
        AppointDepartService appointDepartService = ApplicationUtils.getBasicService(AppointDepartService.class);
        AppointDepartDTO appointDepartDTO = appointDepartService.getByOrganIDAndAppointDepartCode(hisRecipe.getClinicOrgan(), hisRecipe.getDepartCode());
        if (appointDepartDTO != null) {
            recipe.setDepart(appointDepartDTO.getDepartId());
        } else {
            LOGGER.info("HisRecipeService saveRecipeFromHisRecipe 无法查询到挂号科室:{}.", hisRecipe.getDepartCode());
        }
        EmploymentService employmentService = BasicAPI.getService(EmploymentService.class);
        if (StringUtils.isNotEmpty(hisRecipe.getDoctorCode())) {
            EmploymentDTO employmentDTO = employmentService.getByJobNumberAndOrganId(hisRecipe.getDoctorCode(), hisRecipe.getClinicOrgan());
            if (employmentDTO != null && employmentDTO.getDoctorId() != null) {
                recipe.setDoctor(employmentDTO.getDoctorId());
            } else {
                LOGGER.error("请确认医院的医生工号和纳里维护的是否一致:" + hisRecipe.getDoctorCode());
                throw new DAOException(ErrorCode.SERVICE_ERROR, "请将医院的医生工号和纳里维护的医生工号保持一致");
            }
        }

        recipe.setDoctorName(hisRecipe.getDoctorName());
        recipe.setCreateDate(hisRecipe.getCreateDate());
        recipe.setSignDate(hisRecipe.getCreateDate());
        recipe.setOrganDiseaseName(hisRecipe.getDiseaseName());
        recipe.setOrganDiseaseId(hisRecipe.getDisease());
        recipe.setTotalMoney(hisRecipe.getRecipeFee());
        recipe.setActualPrice(hisRecipe.getRecipeFee());
        recipe.setMemo(hisRecipe.getMemo()==null?"无":hisRecipe.getMemo());
        recipe.setPayFlag(0);
        if (hisRecipe.getStatus() == 2) {
            recipe.setStatus(6);
        } else {
            recipe.setStatus(2);
        }

        recipe.setReviewType(0);
        recipe.setChooseFlag(0);
        recipe.setRemindFlag(0);
        recipe.setPushFlag(0);
        recipe.setTakeMedicine(0);
        recipe.setGiveFlag(0);
        recipe.setRecipeMode("ngarihealth");
        recipe.setCopyNum(1);
        recipe.setValueDays(3);
        recipe.setFromflag(1);
        recipe.setRecipeSourceType(2);
        recipe.setRecipePayType(hisRecipe.getRecipePayType());
        recipe.setRequestMpiId(hisRecipe.getMpiId());
        recipe.setRecipeSource(hisRecipe.getRecipeSource());
        recipe.setGiveMode(hisRecipe.getGiveMode());
        recipe.setLastModify(new Date());
        //中药
        recipe.setCopyNum(StringUtils.isEmpty(hisRecipe.getTcmNum())==true?null:Integer.parseInt(hisRecipe.getTcmNum()));
        return recipeDAO.saveRecipe(recipe);

    }

    private void savaRecipeDetail(Integer recipeId, HisRecipe hisRecipe) {
        List<HisRecipeDetail> hisRecipeDetails = hisRecipeDetailDAO.findByHisRecipeId(hisRecipe.getHisRecipeID());
        OrganDrugListDAO organDrugListDAO = DAOFactory.getDAO(OrganDrugListDAO.class);
        List<Recipedetail> recipedetails = recipeDetailDAO.findByRecipeId(recipeId);
        if (CollectionUtils.isNotEmpty(recipedetails)) {
            return;
        }
        for (HisRecipeDetail hisRecipeDetail : hisRecipeDetails) {
            LOGGER.info("hisRecipe.getClinicOrgan(): "+hisRecipe.getClinicOrgan()+"");
            LOGGER.info("Arrays.asList(hisRecipeDetail.getDrugCode()):"+hisRecipeDetail.getDrugCode());
            List<OrganDrugList> organDrugLists = organDrugListDAO.findByOrganIdAndDrugCodes(hisRecipe.getClinicOrgan(), Arrays.asList(hisRecipeDetail.getDrugCode()));
            if (CollectionUtils.isEmpty(organDrugLists)) {
                throw new DAOException(ErrorCode.SERVICE_ERROR, "请将医院的药品信息维护到纳里机构药品目录");
            }
            Recipedetail recipedetail = new Recipedetail();
            recipedetail.setRecipeId(recipeId);
            recipedetail.setUseDoseUnit(hisRecipeDetail.getUseDoseUnit());
            //用量纯数字的存useDose,非数字的存useDoseStr
            if(!StringUtils.isEmpty(hisRecipeDetail.getUseDose())){
                try{
                    recipedetail.setUseDose(Double.valueOf(hisRecipeDetail.getUseDose()));//高优先级
                }catch (Exception e){
                    recipedetail.setUseDoseStr(hisRecipeDetail.getUseDose() + hisRecipeDetail.getUseDoseUnit());
                }
            }
            //  线下特殊用法
            if (!StringUtils.isEmpty(hisRecipeDetail.getUseDoseStr())) {
                try{
                    if(recipedetail.getUseDose()==null){
                        recipedetail.setUseDose(Double.valueOf(hisRecipeDetail.getUseDoseStr()));
                    }
                }catch (Exception e){
                    recipedetail.setUseDoseStr(hisRecipeDetail.getUseDoseStr() + hisRecipeDetail.getUseDoseUnit());//高优先级
                }
            }

            if (StringUtils.isNotEmpty(hisRecipeDetail.getDrugSpec())) {
                recipedetail.setDrugSpec(hisRecipeDetail.getDrugSpec());
            } else {
                if (CollectionUtils.isNotEmpty(organDrugLists)) {
                    recipedetail.setDrugSpec(organDrugLists.get(0).getDrugSpec());
                }
            }
            if (StringUtils.isNotEmpty(hisRecipeDetail.getDrugName())) {
                recipedetail.setDrugName(hisRecipeDetail.getDrugName());
            } else {
                if (CollectionUtils.isNotEmpty(organDrugLists)) {
                    recipedetail.setDrugName(organDrugLists.get(0).getDrugName());
                }
            }
            if (StringUtils.isNotEmpty(hisRecipeDetail.getDrugUnit())) {
                recipedetail.setDrugUnit(hisRecipeDetail.getDrugUnit());
            } else {
                if (CollectionUtils.isNotEmpty(organDrugLists)) {
                    recipedetail.setDrugUnit(organDrugLists.get(0).getUnit());
                }
            }
            if (hisRecipeDetail.getPack() != null) {
                recipedetail.setPack(hisRecipeDetail.getPack());
            } else {
                if (CollectionUtils.isNotEmpty(organDrugLists)) {
                    recipedetail.setPack(organDrugLists.get(0).getPack());
                }
            }
            if (hisRecipeDetail.getPrice() != null) {
                recipedetail.setSalePrice(hisRecipeDetail.getPrice());
            }

            if (CollectionUtils.isNotEmpty(organDrugLists)) {
                recipedetail.setDrugId(organDrugLists.get(0).getDrugId());
                recipedetail.setOrganDrugCode(hisRecipeDetail.getDrugCode());
                //recipedetail.setUsingRate(organDrugLists.get(0).getUsingRate());
                //recipedetail.setUsePathways(organDrugLists.get(0).getUsePathways());
                if (StringUtils.isEmpty(recipedetail.getUseDoseUnit())) {
                    recipedetail.setUseDoseUnit(organDrugLists.get(0).getUseDoseUnit());
                }
            }
            recipedetail.setUsingRateTextFromHis(hisRecipeDetail.getUsingRateText());
            recipedetail.setUsePathwaysTextFromHis(hisRecipeDetail.getUsePathwaysText());
            if (hisRecipeDetail.getUseTotalDose() != null) {
                recipedetail.setUseTotalDose(hisRecipeDetail.getUseTotalDose().doubleValue());
            }
            recipedetail.setUseDays(hisRecipeDetail.getUseDays());
            //date 20200528
            //设置线上处方的信息
            recipedetail.setUseDaysB(hisRecipeDetail.getUseDaysB());
            recipedetail.setStatus(1);

            //单药品总价使用线下传过来的，传过来多少就是多少我们不计算
            if (hisRecipeDetail.getTotalPrice() != null) {
                recipedetail.setDrugCost(hisRecipeDetail.getTotalPrice());
            }
            recipeDetailDAO.save(recipedetail);
        }
    }

    private String getRecipeStatusTabText(int status) {
        String msg;
        switch (status) {
            case RecipeStatusConstant.FINISH:
                msg = "已完成";
                break;
            case RecipeStatusConstant.HAVE_PAY:
                msg = "已支付，待取药";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                msg = "待处理";
                break;
            case RecipeStatusConstant.NO_PAY:
                msg = "未支付";
                break;
            case RecipeStatusConstant.NO_OPERATOR:
                msg = "未处理";
                break;
            //已撤销从已取消拆出来
            case RecipeStatusConstant.REVOKE:
                msg = "已撤销";
                break;
            //已撤销从已取消拆出来
            case RecipeStatusConstant.DELETE:
                msg = "已删除";
                break;
            //写入his失败从已取消拆出来
            case RecipeStatusConstant.HIS_FAIL:
                msg = "写入his失败";
                break;
            case RecipeStatusConstant.CHECK_NOT_PASS_YS:
                msg = "审核不通过";
                break;
            case RecipeStatusConstant.IN_SEND:
                msg = "配送中";
                break;
            case RecipeStatusConstant.WAIT_SEND:
                msg = "待配送";
                break;
            case RecipeStatusConstant.READY_CHECK_YS:
                msg = "待审核";
                break;
            case RecipeStatusConstant.CHECK_PASS_YS:
                msg = "审核通过";
                break;
            //这里患者取药失败和取药失败都判定为失败
            case RecipeStatusConstant.NO_DRUG:
            case RecipeStatusConstant.RECIPE_FAIL:
                msg = "失败";
                break;
            case RecipeStatusConstant.RECIPE_DOWNLOADED:
                msg = "待取药";
                break;
            case RecipeStatusConstant.USING:
                msg = "处理中";
                break;
            default:
                msg = "未知状态";
        }

        return msg;
    }


    /**
     * 状态文字提示（患者端）
     *
     * @param recipe
     * @return
     */
    public static String getTipsByStatusForPatient(Recipe recipe, RecipeOrder order) {
        Integer status = recipe.getStatus();
        Integer payMode = recipe.getPayMode();
        Integer payFlag = recipe.getPayFlag();
        Integer giveMode = recipe.getGiveMode();
        Integer orderStatus = order.getStatus();
        String tips = "";
        switch (status) {
            case RecipeStatusConstant.HIS_FAIL:
                tips = "已取消";
                break;
            case RecipeStatusConstant.FINISH:
                tips = "已完成";
                break;
            case RecipeStatusConstant.IN_SEND:
                tips = "配送中";
                break;
            case RecipeStatusConstant.CHECK_PASS:
                if (null == payMode || null == giveMode) {
                    tips = "待处理";
                } else if (RecipeBussConstant.PAYMODE_TO_HOS.equals(payMode)) {
                    if (new Integer(1).equals(recipe.getRecipePayType()) && payFlag == 1) {
                        tips = "已支付";
                    } else if (payFlag == 0){
                        tips = "待支付";
                    } else {
                        tips = "待取药";
                    }
                } else if (RecipeBussConstant.GIVEMODE_SEND_TO_HOME.equals(giveMode)) {
                    if (StringUtils.isNotEmpty(recipe.getOrderCode())) {
                        if (payFlag == 0) {
                            tips = "待支付";
                        } else {
                            if (OrderStatusConstant.READY_SEND.equals(orderStatus)) {
                                tips = "待配送";
                            } else if (OrderStatusConstant.SENDING.equals(orderStatus)) {
                                tips = "配送中";
                            } else if (OrderStatusConstant.FINISH.equals(orderStatus)) {
                                tips = "已完成";
                            }
                        }
                    }

                } else if (RecipeBussConstant.GIVEMODE_TFDS.equals(giveMode) && StringUtils.isNotEmpty(recipe.getOrderCode())) {
                    if (OrderStatusConstant.HAS_DRUG.equals(orderStatus)) {
                        if (payFlag == 0) {
                            tips = "待支付";
                        } else {
                            tips = "待取药";
                        }
                    }
                } else if (RecipeBussConstant.GIVEMODE_DOWNLOAD_RECIPE.equals(giveMode)) {
                    tips = "已完成";
                }
                break;
            default:
                tips = "待取药";
        }
        return tips;
    }

    @RpcService
    public Integer getCardType(Integer organId) {
        //卡类型 1 表示身份证  2 表示就诊卡
        IConfigurationCenterUtilsService configurationCenterUtilsService = ApplicationUtils.getBaseService(IConfigurationCenterUtilsService.class);
        return (Integer) configurationCenterUtilsService.getConfiguration(organId, "getCardTypeForHis");
    }

    /**
     * 校验 his线下处方是否有更改
     *
     * @param hisRecipeTO
     */
    private void hisRecipeInfoCheck(List<QueryHisRecipResTO> hisRecipeTO, PatientDTO patientDTO) {
        LOGGER.info("hisRecipeInfoCheck hisRecipeTO = {}.", JSONUtils.toString(hisRecipeTO));
        if(CollectionUtils.isEmpty(hisRecipeTO)){
            return;
        }
        Integer clinicOrgan = hisRecipeTO.get(0).getClinicOrgan();
        if (null == clinicOrgan) {
            LOGGER.info("hisRecipeInfoCheck his data error clinicOrgan is null");
            return;
        }
        List<String> recipeCodeList = hisRecipeTO.stream().map(QueryHisRecipResTO::getRecipeCode).distinct().collect(Collectors.toList());
        if (CollectionUtils.isEmpty(recipeCodeList)) {
            LOGGER.info("hisRecipeInfoCheck his data error recipeCodeList is null");
            return;
        }
        //2 判断Recipe 是否有订单
        List<Recipe> recipeList = recipeDAO.findByRecipeCodeAndClinicOrgan(recipeCodeList, clinicOrgan);
        LOGGER.info("hisRecipeInfoCheck recipeList = {}", JSONUtils.toString(recipeList));

        if (CollectionUtils.isNotEmpty(recipeList)) {
            List<String> orderCodeList = recipeList.stream().filter(a -> StringUtils.isNotEmpty(a.getOrderCode())).map(Recipe::getOrderCode).distinct().collect(Collectors.toList());
            //3 判断 订单 是否支付
            if (CollectionUtils.isNotEmpty(orderCodeList)) {
                List<RecipeOrder> recipeOrderList = recipeOrderDAO.findByOrderCode(orderCodeList);
                LOGGER.info("hisRecipeInfoCheck recipeOrderList = {}", JSONUtils.toString(recipeOrderList));
                List<String> recipeOrderCode = recipeOrderList.stream().filter(a -> a.getPayFlag().equals(PayConstant.PAY_FLAG_PAY_SUCCESS)).map(RecipeOrder::getOrderCode).collect(Collectors.toList());
                List<String> recipeCodeExclude = recipeList.stream().filter(a -> recipeOrderCode.contains(a.getOrderCode())).map(Recipe::getRecipeCode).distinct().collect(Collectors.toList());
                //排除支付订单处方（找到非已支付的recipeCode）
                recipeCodeList = recipeCodeList.stream().filter(a -> !recipeCodeExclude.contains(a)).collect(Collectors.toList());
                //question TODO 比如his 返回已缴费的处方 ordercode就是空 这样吧his返回的已缴费的处方也过滤出来了


                LOGGER.info("hisRecipeInfoCheck recipeCodeList = {}", JSONUtils.toString(recipeCodeList));
                if (CollectionUtils.isEmpty(recipeCodeList)) {
                    return;
                }
            }
        }

        List<HisRecipe> hisRecipeList = hisRecipeDAO.findHisRecipeByRecipeCodeAndClinicOrgan(clinicOrgan, recipeCodeList);
        LOGGER.info("hisRecipeInfoCheck hisRecipeList = {}", JSONUtils.toString(hisRecipeList));
        if (CollectionUtils.isEmpty(hisRecipeList)) {
            return;
        }
        //判断 hisRecipe 诊断不一致 更新
        Map<String, HisRecipe> hisRecipeMap = updateHisRecipe(hisRecipeTO, recipeList, hisRecipeList);

        /**判断处方是否删除*/
        List<Integer> hisRecipeIds = hisRecipeList.stream().map(HisRecipe::getHisRecipeID).distinct().collect(Collectors.toList());
        List<HisRecipeDetail> hisRecipeDetailList = hisRecipeDetailDAO.findByHisRecipeIds(hisRecipeIds);
        LOGGER.info("hisRecipeInfoCheck hisRecipeDetailList = {}", JSONUtils.toString(hisRecipeDetailList));
        if (CollectionUtils.isEmpty(hisRecipeDetailList)) {
            return;
        }

        //1 判断是否delete 处方相关表 / RecipeDetailTO 数量 ，药品，开药总数
        Set<String> deleteSetRecipeCode = new HashSet<>();
        Map<Integer, List<HisRecipeDetail>> hisRecipeIdDetailMap = hisRecipeDetailList.stream().collect(Collectors.groupingBy(HisRecipeDetail::getHisRecipeId));
        hisRecipeTO.forEach(a -> {
            String recipeCode = a.getRecipeCode();
            HisRecipe hisRecipe = hisRecipeMap.get(recipeCode);
            if (null == hisRecipe) {
                return;
            } else {
                if (!hisRecipe.getMpiId().equals(patientDTO.getMpiId())) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause mpiid recipeCode:{}",recipeCode);
                    return;
                }
            }
            List<HisRecipeDetail> hisDetailList = hisRecipeIdDetailMap.get(hisRecipe.getHisRecipeID());
            if (CollectionUtils.isEmpty(a.getDrugList()) || CollectionUtils.isEmpty(hisDetailList)) {
                deleteSetRecipeCode.add(recipeCode);
                LOGGER.info("deleteSetRecipeCode cause drugList empty recipeCode:{}",recipeCode);

                return;
            }
            if (a.getDrugList().size() != hisDetailList.size()) {
                deleteSetRecipeCode.add(recipeCode);
                LOGGER.info("deleteSetRecipeCode cause drugList size no equal recipeCode:{}",recipeCode);
                return;
            }
            Map<String, HisRecipeDetail> recipeDetailMap = hisDetailList.stream().collect(Collectors.toMap(HisRecipeDetail::getDrugCode, b -> b, (k1, k2) -> k1));
            for (RecipeDetailTO recipeDetailTO : a.getDrugList()) {
                HisRecipeDetail hisRecipeDetail = recipeDetailMap.get(recipeDetailTO.getDrugCode());
                if (null == hisRecipeDetail) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause hisRecipeDetail is null recipeCode:{}",recipeCode);
                    continue;
                }
                BigDecimal useTotalDose = hisRecipeDetail.getUseTotalDose();
                if (null == useTotalDose || 0 != useTotalDose.compareTo(recipeDetailTO.getUseTotalDose())) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause useTotalDose recipeCode:{}",recipeCode);
                    continue;
                }
                String useDose = hisRecipeDetail.getUseDose();
                if ((StringUtils.isEmpty(useDose) && StringUtils.isNotEmpty(recipeDetailTO.getUseDose())) || (StringUtils.isNotEmpty(useDose) && !useDose.equals(recipeDetailTO.getUseDose()))) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause useDose recipeCode:{}",recipeCode);
                    continue;
                }
                String useDoseStr = hisRecipeDetail.getUseDoseStr();
                if ((StringUtils.isEmpty(useDoseStr) && StringUtils.isNotEmpty(recipeDetailTO.getUseDoseStr())) || (StringUtils.isNotEmpty(useDoseStr) && !useDoseStr.equals(recipeDetailTO.getUseDoseStr()))) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause useDoseStr recipeCode:{}",recipeCode);
                    continue;
                }
                Integer useDays = hisRecipeDetail.getUseDays();
                if ((useDays == null && recipeDetailTO.getUseDays() != null) || (useDays != null && !useDays.equals(recipeDetailTO.getUseDays()))) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause useDays recipeCode:{}",recipeCode);
                    continue;
                }
                String usingRate = hisRecipeDetail.getUsingRate();
                if ((StringUtils.isEmpty(usingRate) && StringUtils.isNotEmpty(recipeDetailTO.getUsingRate())) || (StringUtils.isNotEmpty(usingRate) && !usingRate.equals(recipeDetailTO.getUsingRate()))) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause usingRate recipeCode:{}",recipeCode);
                    continue;
                }

                String usingRateText = hisRecipeDetail.getUsingRateText();
                if ((StringUtils.isEmpty(usingRateText) && StringUtils.isNotEmpty(recipeDetailTO.getUsingRateText())) || (StringUtils.isNotEmpty(usingRateText) && !usingRateText.equals(recipeDetailTO.getUsingRateText()))) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause usingRateText recipeCode:{}",recipeCode);
                    continue;
                }
                String usePathways = hisRecipeDetail.getUsePathways();
                if ((StringUtils.isEmpty(usePathways) && StringUtils.isNotEmpty(recipeDetailTO.getUsePathWays())) || (StringUtils.isNotEmpty(usePathways) && !usingRateText.equals(recipeDetailTO.getUsePathWays()))) {
                    deleteSetRecipeCode.add(recipeCode);
                    LOGGER.info("deleteSetRecipeCode cause usePathWays recipeCode:{}",recipeCode);
                    continue;
                }
                String usePathwaysText = hisRecipeDetail.getUsePathwaysText();
                if ((StringUtils.isEmpty(usePathwaysText) && StringUtils.isNotEmpty(recipeDetailTO.getUsePathwaysText())) || (StringUtils.isNotEmpty(usePathwaysText) && !usePathwaysText.equals(recipeDetailTO.getUsePathwaysText()))) {
                    LOGGER.info("deleteSetRecipeCode cause usePathwaysText recipeCode:{}",recipeCode);
                    deleteSetRecipeCode.add(recipeCode);
                }
            }
            //中药判断tcmFee发生变化,删除数据
            BigDecimal tcmFee =  a.getTcmFee() ;
            if(tcmFee.compareTo(hisRecipe.getTcmFee())!= 0){
                LOGGER.info("deleteSetRecipeCode cause tcmFee recipeCode:{}",recipeCode);
                deleteSetRecipeCode.add(hisRecipe.getRecipeCode());
            }

        });

        //删除
        deleteSetRecipeCode(clinicOrgan, deleteSetRecipeCode);
    }

    /**
     * 删除线下处方相关数据
     *
     * @param clinicOrgan         机构id
     * @param deleteSetRecipeCode 要删除的
     */
    void deleteSetRecipeCode(Integer clinicOrgan, Set<String> deleteSetRecipeCode) {
        LOGGER.info("deleteSetRecipeCode clinicOrgan = {},deleteSetRecipeCode = {}", clinicOrgan, JSONUtils.toString(deleteSetRecipeCode));
        if (CollectionUtils.isEmpty(deleteSetRecipeCode)) {
            return;
        }
        List<String> recipeCodeList = new ArrayList<>(deleteSetRecipeCode);
        List<HisRecipe> hisRecipeList = hisRecipeDAO.findHisRecipeByRecipeCodeAndClinicOrgan(clinicOrgan, recipeCodeList);
        List<Integer> hisRecipeIds = hisRecipeList.stream().map(HisRecipe::getHisRecipeID).collect(Collectors.toList());
        hisRecipeExtDAO.deleteByHisRecipeIds(hisRecipeIds);
        hisRecipeDetailDAO.deleteByHisRecipeIds(hisRecipeIds);
        hisRecipeDAO.deleteByHisRecipeIds(hisRecipeIds);
        List<Recipe> recipeList = recipeDAO.findByRecipeCodeAndClinicOrgan(recipeCodeList, clinicOrgan);
        if (CollectionUtils.isEmpty(recipeList)) {
            return;
        }
        List<Integer> recipeIds = recipeList.stream().map(Recipe::getRecipeId).collect(Collectors.toList());
        List<String> orderCodeList = recipeList.stream().filter(a -> StringUtils.isNotEmpty(a.getOrderCode())).map(Recipe::getOrderCode).collect(Collectors.toList());
        if (CollectionUtils.isNotEmpty(orderCodeList)) {
            recipeOrderDAO.deleteByRecipeIds(orderCodeList);
        }
        recipeExtendDAO.deleteByRecipeIds(recipeIds);
        recipeDetailDAO.deleteByRecipeIds(recipeIds);
        //question TODO recipe 主表不删 修改具体哪些字段（除id外所有字段）
        recipeDAO.deleteByRecipeIds(recipeIds);
        LOGGER.info("deleteSetRecipeCode is delete end ");
    }

    /**
     * 更新诊断字段
     *
     * @param hisRecipeTO
     * @param recipeList
     * @param hisRecipeList
     */
    private Map<String, HisRecipe> updateHisRecipe(List<QueryHisRecipResTO> hisRecipeTO, List<Recipe> recipeList, List<HisRecipe> hisRecipeList) {
        Map<String, Recipe> recipeMap = recipeList.stream().collect(Collectors.toMap(Recipe::getRecipeCode, a -> a, (k1, k2) -> k1));
        Map<String, HisRecipe> hisRecipeMap = hisRecipeList.stream().collect(Collectors.toMap(HisRecipe::getRecipeCode, a -> a, (k1, k2) -> k1));
        hisRecipeTO.forEach(a -> {
            HisRecipe hisRecipe = hisRecipeMap.get(a.getRecipeCode());
            if (null == hisRecipe) {
                return;
            }
            String disease = null != a.getDisease() ? a.getDisease() : "";
            String diseaseName = null != a.getDiseaseName() ? a.getDiseaseName() : "";
            if (!disease.equals(hisRecipe.getDisease()) || !diseaseName.equals(hisRecipe.getDiseaseName())) {
                hisRecipe.setDisease(disease);
                hisRecipe.setDiseaseName(diseaseName);
                hisRecipeDAO.update(hisRecipe);
                LOGGER.info("updateHisRecipe hisRecipe = {}", JSONUtils.toString(hisRecipe));
                Recipe recipe = recipeMap.get(a.getRecipeCode());
                RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
                RecipeBean recipeBean = new RecipeBean();
                BeanUtils.copy(recipe, recipeBean);
                emrRecipeManager.updateMedicalInfo(recipeBean, recipeExtend);
                recipeExtendDAO.saveOrUpdateRecipeExtend(recipeExtend);
            }
        });
        return hisRecipeMap;
    }

}
