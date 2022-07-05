package recipe.drugTool.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.base.serviceconfig.mode.ServiceConfigResponseTO;
import com.ngari.base.serviceconfig.service.IHisServiceConfigService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.regulation.service.IRegulationService;
import com.ngari.infra.logistics.mode.WriteBackLogisticsOrderDto;
import com.ngari.infra.logistics.service.ILogisticsOrderService;
import com.ngari.opbase.base.service.IBusActionLogService;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.patient.dto.UsePathwaysDTO;
import com.ngari.patient.dto.UsingRateDTO;
import com.ngari.patient.service.*;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.RecipeAPI;
import com.ngari.recipe.drug.model.DrugListBean;
import com.ngari.recipe.drug.model.ProvinceDrugListBean;
import com.ngari.recipe.drug.service.IOrganDrugListService;
import com.ngari.recipe.drug.service.ISaleDrugListService;
import com.ngari.recipe.drugTool.service.IDrugToolService;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.UpdateMatchStatusFormBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.spring.AppDomainContext;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import ctd.util.event.GlobalEventExecFactory;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.map.HashedMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import recipe.aop.LogRecord;
import recipe.bean.DoctorDrugUsageRequest;
import recipe.bean.DrugEnterpriseResult;
import recipe.bean.OrganToolBean;
import recipe.constant.DrugEnterpriseConstant;
import recipe.constant.DrugMatchConstant;
import recipe.constant.ErrorCode;
import recipe.constant.RecipeSystemConstant;
import recipe.dao.*;
import recipe.service.DrugsEnterpriseConfigService;
import recipe.service.OrganDrugListService;
import recipe.thread.RecipeBusiThreadPool;
import recipe.util.DrugMatchUtil;
import recipe.util.LocalStringUtil;
import recipe.util.Md5Utils;
import recipe.util.RedisClient;
import recipe.vo.greenroom.ImportDrugRecordVO;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * created by shiyuping on 2019/2/1
 */
@RpcBean(value = "drugToolService", mvc_authentication = false)
public class DrugToolService implements IDrugToolService {

    /**
     * 修改匹配中的原状态（已提交，已匹配）
     */
    public static final Integer[] Change_Matching_StatusList = {DrugMatchConstant.ALREADY_MATCH, DrugMatchConstant.SUBMITED};
    /**
     * 修改匹配中的原状态（已提交，已匹配）
     */
    public static final Integer[] Ready_Match_StatusList = {DrugMatchConstant.ALREADY_MATCH, DrugMatchConstant.SUBMITED};
    private static final Logger LOGGER = LoggerFactory.getLogger(DrugToolService.class);
    private static final String SUFFIX_2003 = ".xls";
    private static final String SUFFIX_2007 = ".xlsx";
    /*平台类型*/
    private static final int Platform_Type = 0;
    /*省平台类型*/
    private static final int Province_Platform_Type = 1;
    private double progress;
    private RedisClient redisClient = RedisClient.instance();
    //全局map
    private ConcurrentHashMap<String, Double> progressMap = new ConcurrentHashMap<>();
    /**
     * 用于药品小工具搜索历史记录缓存
     */
    private ConcurrentHashMap<String, ArrayBlockingQueue> cmap = new ConcurrentHashMap<>();
    @Resource
    private DrugListMatchDAO drugListMatchDAO;

    @Resource
    private DrugListDAO drugListDAO;

    @Resource
    private SaleDrugListDAO saleDrugListDAO;

    @Resource
    private OrganDrugListDAO organDrugListDAO;

    @Resource
    private DrugToolUserDAO drugToolUserDAO;

    @Resource
    private OrganService organService;
    @Resource
    private OrganDrugListService organDrugListService;

    @Resource
    private SaleDrugToolService saleDrugToolService;

    @Resource
    private ProvinceDrugListDAO provinceDrugListDAO;

    @Resource
    private ImportDrugRecordDAO importDrugRecordDAO;

    @Resource
    private PharmacyTcmDAO pharmacyTcmDAO;

    @Autowired
    private DrugEntrustDAO drugEntrustDAO;

    @Autowired
    private DrugsEnterpriseDAO drugsEnterpriseDAO;




    private LoadingCache<String, List<DrugList>> drugListCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build(new CacheLoader<String, List<DrugList>>() {
        @Override
        public List<DrugList> load(String str) throws Exception {
            return drugListDAO.findBySaleNameLike(str);
        }
    });


    @RpcService
    public void resetMatchCache() {
        drugListCache.cleanUp();
    }


    @RpcService
    public DrugToolUser loginOrRegist(String name, String mobile, String pwd) {
        if (StringUtils.isEmpty(name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "name is required");
        }
        if (StringUtils.isEmpty(mobile)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mobile is required");
        }
        if (StringUtils.isEmpty(pwd)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "pwd is required");
        }
        DrugToolUser dbUser = drugToolUserDAO.getByMobile(mobile);
        if (dbUser == null) {
            DrugToolUser user = new DrugToolUser();
            user.setName(name);
            user.setMobile(mobile);
            user.setPassword(pwd);
            user.setStatus(1);
            dbUser = drugToolUserDAO.save(user);
        } else {
            if (!(pwd.equals(dbUser.getPassword()) && name.equals(dbUser.getName()))) {
                throw new DAOException(609, "姓名或密码不正确");
            }
        }
        return dbUser;
    }

    @RpcService
    public boolean isLogin(String mobile) {
        if (StringUtils.isEmpty(mobile)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "mobile is required");
        }
        boolean result = false;
        DrugToolUser dbUser = drugToolUserDAO.getByMobile(mobile);
        if (dbUser != null) {
            result = true;
        }
        return result;
    }

    //获取进度条
    @RpcService
    public double getProgress(int organId, String operator) throws InterruptedException {
        String key = organId + operator;
//      Double data = progressMap.get(key);
        Double data = redisClient.get(key);
        if (data != null) {
            progress = data;
            if (progress == 100 && redisClient.exists(key)) {
//                 progressMap.remove(key);
                redisClient.del(key);
            }
        }
        LOGGER.info("进度条加载={}=", progress);
        return progress;
    }

    @Override
    @LogRecord
    public Map<String, Object> readDrugExcel(byte[] buf, String originalFilename, int organId, String operator) {
        LOGGER.info(operator + "开始 readDrugExcel 方法" + System.currentTimeMillis() + "当前进程=" + Thread.currentThread().getName());
        String key = organId + operator;
        Map<String, Object> result = Maps.newHashMap();
        if (StringUtils.isEmpty(operator)) {
            result.put("code", 609);
            result.put("msg", "operator is required");
            return result;
        }
        int length = buf.length;
        LOGGER.info("readDrugExcel byte[] length=" + length);
        int max = 1343518;
        //控制导入数据量
        if (max <= length) {
            result.put("code", 609);
            result.put("msg", "超过7000条数据,请分批导入");
            return result;
        }
        //获得用户上传工作簿
        Workbook workbook = null;
        try {
            InputStream is = new ByteArrayInputStream(buf);
            if (originalFilename.endsWith(SUFFIX_2003)) {
                LOGGER.info("readDrugExcel SUFFIX_2003");
                workbook = new HSSFWorkbook(is);
            } else if (originalFilename.endsWith(SUFFIX_2007)) {
                //使用InputStream需要将所有内容缓冲到内存中，这会占用空间并占用时间
                //当数据量过大时，这里会非常耗时
                LOGGER.info("readDrugExcel SUFFIX_2007");
                workbook = new XSSFWorkbook(is);
            } else {
                result.put("code", 609);
                result.put("msg", "上传文件格式有问题");
                return result;
            }
        } catch (Exception e) {
            LOGGER.error("readDrugExcel error ," + e.getMessage(), e);
            result.put("code", 609);
            result.put("msg", "上传文件格式有问题");
            //e.printStackTrace();
            return result;
        }
        Sheet sheet = workbook.getSheetAt(0);
        Integer total = sheet.getLastRowNum();
        if (total == null || total <= 0) {
            result.put("code", 609);
            result.put("msg", "data is required");
            return result;
        }

        DrugListMatch drug;
        Row row;
        List<String> errDrugListMatchList = Lists.newArrayList();
        Integer addNum = 0;
        Integer updateNum = 0;
        Integer failNum = 0;
        LOGGER.info("机构药品目录导入数据校验开始,文件名={},organId={},operator={}", originalFilename, organId, operator);
        int bankNumber = 0;
        for (int rowIndex = 0; rowIndex <= total; rowIndex++) {
            //循环获得每个行
            row = sheet.getRow(rowIndex);
            boolean flag = false;
            if(null != row){
                for(Cell cell : row){
                    if (cell.getCellType() != Cell.CELL_TYPE_BLANK) {
                        flag = true;
                        break;
                    }
                }
            }
            //如果为空行的数据，则空行数+1
            if(!flag) {
                bankNumber +=1;
            }
            //否则正常读取数据
            else {
                // 判断是否是模板
                if (rowIndex == 0) {
                    String drugCode = getStrFromCell(row.getCell(0));
                    String drugName = getStrFromCell(row.getCell(1));
                    String retrievalCode = getStrFromCell(row.getCell(32));
                    if ("监管药品编码".equals(drugCode) && "平台药品编码".equals(drugName) && "单复方".equals(retrievalCode)) {
                        continue;
                    } else {
                        result.put("code", 609);
                        result.put("msg", "模板有误，请确认！");
                        return result;
                    }

                }
                drug = new DrugListMatch();
                StringBuilder errMsg = new StringBuilder();
                /*try{*/
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(0)))) {
                        drug.setRegulationDrugCode(getStrFromCell(row.getCell(0)));
                    }
                } catch (Exception e) {
                    LOGGER.error("监管药品编码," + e.getMessage(), e);
                    errMsg.append("监管药品编码有误").append(";");
                }


                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(2)))) {
                        errMsg.append("机构唯一索引不能为空").append(";");
                    }
                    drug.setOrganDrugCode(getStrFromCell(row.getCell(2)));
                } catch (Exception e) {
                    LOGGER.error("机构唯一索引有误 ," + e.getMessage(), e);
                    errMsg.append("机构唯一索引有误").append(";");
                }


                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(3)))) {
                        drug.setDrugItemCode(getStrFromCell(row.getCell(3)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品编码 ," + e.getMessage(), e);
                    errMsg.append("药品编码").append(";");
                }


                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(1)))) {
                        OrganDrugList byDrugIdAndOrganId = organDrugListDAO.getByDrugIdAndOrganId(Integer.parseInt(getStrFromCell(row.getCell(1)).trim()), organId);
                        if (!StringUtils.isEmpty(getStrFromCell(row.getCell(2)))) {
                            if (!ObjectUtils.isEmpty(byDrugIdAndOrganId)) {
                                if (!byDrugIdAndOrganId.getOrganDrugCode().equals(getStrFromCell(row.getCell(2)))) {
                                    errMsg.append("机构已存在药品关联该平台药品").append(";");
                                }
                            }
                        }
                        drug.setPlatformDrugId(Integer.parseInt(getStrFromCell(row.getCell(1)).trim()));
                    }
                } catch (Exception e) {
                    LOGGER.error("平台药品编码有误 ," + e.getMessage(), e);
                    errMsg.append("平台药品编码有误").append(";");
                }

                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(4)))) {
                        errMsg.append("【药品名】未填写").append(";");
                    }
                    drug.setDrugName(getStrFromCell(row.getCell(4)));
                } catch (Exception e) {
                    LOGGER.error("药品名有误 ," + e.getMessage(), e);
                    errMsg.append("药品名有误").append(";");
                }
                try {
                    drug.setSaleName(getStrFromCell(row.getCell(5)));
                } catch (Exception e) {
                    LOGGER.error("药品商品名有误 ," + e.getMessage(), e);
                    errMsg.append("药品商品名有误").append(";");
                }

                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(6)))) {
                        drug.setChemicalName(getStrFromCell(row.getCell(6)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品化学名有误," + e.getMessage(), e);
                    errMsg.append("药品化学名有误").append(";");
                }

                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(7)))) {
                        errMsg.append("【院内检索码】未填写").append(";");
                    }
                    drug.setRetrievalCode(getStrFromCell(row.getCell(7)));
                } catch (Exception e) {
                    LOGGER.error("药品院内检索码有误 ," + e.getMessage(), e);
                    errMsg.append("药品院内检索码有误").append(";");
                }
           /* try {
                if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(7)))) {
                    if ("有效".equals(getStrFromCell(row.getCell(7)).trim())){
                        drug.setStatus(1);
                    }
                }
            } catch (Exception e) {
                LOGGER.error("药品状态有误 ," + e.getMessage(), e);
                errMsg.append("药品状态有误").append(";");
            }*/
                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(9)))) {
                        errMsg.append("【药品类型】未填写").append(";");
                    }
                    if (("中药").equals(getStrFromCell(row.getCell(9)))) {
                        drug.setDrugType(3);
                    } else if (("中成药").equals(getStrFromCell(row.getCell(9)))) {
                        drug.setDrugType(2);
                    } else if (("西药").equals(getStrFromCell(row.getCell(9)))) {
                        drug.setDrugType(1);
                    } else {
                        errMsg.append("药品类型格式错误").append(";");
                    }
                } catch (Exception e) {
                    LOGGER.error("药品类型有误 ," + e.getMessage(), e);
                    errMsg.append("药品类型有误").append(";");
                }
                try {
                    if (!StringUtils.isEmpty(getStrFromCell(row.getCell(10)))) {
                        drug.setDrugForm(getStrFromCell(row.getCell(10)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品剂型有误 ," + e.getMessage(), e);
                    errMsg.append("药品剂型有误").append(";");
                }
                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(11)))) {
                        errMsg.append("【药品规格/单位】未填写").append(";");
                    }
                    drug.setDrugSpec(getStrFromCell(row.getCell(11)));
                } catch (Exception e) {
                    LOGGER.error("药品规格/单位有误 ," + e.getMessage(), e);
                    errMsg.append("药品规格/单位有误").append(";");
                }
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(12)))) {
                        drug.setUseDoseSmallestUnit(getStrFromCell(row.getCell(12)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品最小单位有误 ," + e.getMessage(), e);
                    errMsg.append("药品最小单位有误").append(";");
                }
                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(13)))) {
                        //中药不需要设置
                        if (!(new Integer(3).equals(drug.getDrugType()))) {
                            errMsg.append("【包装数量(转换系数)】未填写").append(";");
                        } else {
                            drug.setPack(1);
                        }
                    } else {
                        drug.setPack(Integer.parseInt(getStrFromCell(row.getCell(13))));
                    }
                } catch (Exception e) {
                    LOGGER.error("包装数量(转换系数)有误 ," + e.getMessage(), e);
                    errMsg.append("包装数量(转换系数)有误").append(";");
                }
                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(14)))) {
                        //中药不需要设置
                        if (!(new Integer(3).equals(drug.getDrugType()))) {
                            errMsg.append("【单次剂量(规格单位)】未填写").append(";");
                        }

                    } else {
                        drug.setUseDose(Double.parseDouble(getStrFromCell(row.getCell(14))));
                    }
                } catch (Exception e) {
                    LOGGER.error("单次剂量(规格单位)有误 ," + e.getMessage(), e);
                    errMsg.append("单次剂量(规格单位)有误").append(";");
                }
                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(15)))) {
                        //中药不需要设置
                        if (!(new Integer(3).equals(drug.getDrugType()))) {
                            errMsg.append("【规格单位】未填写").append(";");
                        }

                    } else {
                        drug.setUseDoseUnit(getStrFromCell(row.getCell(15)));
                    }
                } catch (Exception e) {
                    LOGGER.error("规格单位有误 ," + e.getMessage(), e);
                    errMsg.append("规格单位有误").append(";");
                }
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(16)))) {
                        drug.setRecommendedUseDose(Double.parseDouble(getStrFromCell(row.getCell(16))));
                    }
                } catch (Exception e) {
                    LOGGER.error("默认单次剂量(规格单位)有误 ," + e.getMessage(), e);
                    errMsg.append("默认单次剂量(规格单位)有误").append(";");
                }
                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(17)))) {
                        //中药不需要设置
                        if (!(new Integer(3).equals(drug.getDrugType()))) {
                            errMsg.append("【包装单位】未填写").append(";");
                        }
                    }
                    drug.setUnit(getStrFromCell(row.getCell(17)));
                } catch (Exception e) {
                    LOGGER.error("药品包装单位有误 ," + e.getMessage(), e);
                    errMsg.append("药品包装单位有误").append(";");
                }
                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(18)))) {
                        errMsg.append("【单价】未填写").append(";");
                    }
                    String priceCell = getStrFromCell(row.getCell(18));
                    drug.setPrice(new BigDecimal(priceCell));
                } catch (Exception e) {
                    LOGGER.error("药品单价有误 ," + e.getMessage(), e);
                    errMsg.append("药品单价有误").append(";");
                }
                try {
                    if (StringUtils.isEmpty(getStrFromCell(row.getCell(19)))) {
                        //中药不需要设置
                        if (!(new Integer(3).equals(drug.getDrugType()))) {
                            errMsg.append("【生产厂家】未填写").append(";");
                        }
                    }
                    drug.setProducer(getStrFromCell(row.getCell(19)));
                } catch (Exception e) {
                    LOGGER.error("药品生产厂家有误 ," + e.getMessage(), e);
                    errMsg.append("药品生产厂家有误").append(";");
                }
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(20)))) {
                        drug.setDrugManfCode(getStrFromCell(row.getCell(20)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品产地编码有误 ," + e.getMessage(), e);
                    errMsg.append("药品产地编码有误").append(";");
                }
                try {
                    if (getStrFromCell(row.getCell(21)) != null) {
                        String strFromCell = getStrFromCell(row.getCell(21));
                        StringBuilder ss = new StringBuilder();
                        String[] split = strFromCell.split(",");
                        for (int i = 0; i < split.length; i++) {
                            Integer idByPharmacyName = pharmacyTcmDAO.getIdByPharmacyNameAndOrganId(split[i], organId);
                            if (idByPharmacyName == null) {
                                errMsg.append("药房名称有误").append(";");
                            } else {
                                if (i != split.length - 1) {
                                    ss.append(idByPharmacyName.toString() + ",");
                                } else {
                                    ss.append(idByPharmacyName.toString());
                                }
                            }
                        }
                        drug.setPharmacy(ss.toString());
                    }
                } catch (Exception e) {
                    LOGGER.error("药房名称有误 ," + e.getMessage(), e);
                }

                try {
                    if (getStrFromCell(row.getCell(22)) != null) {
                        String strFromCell = getStrFromCell(row.getCell(22));
                        StringBuilder ss = new StringBuilder();
                        String[] split = strFromCell.split(",");
                        for (int i = 0; i < split.length; i++) {
                            DrugsEnterprise byEnterpriseCode = drugsEnterpriseDAO.getByEnterpriseCode(split[i], organId);
                            if (byEnterpriseCode == null) {
                                errMsg.append("平台未找到该配送药企" + split[i] + "").append(";");
                            } else {
                                if (i != split.length - 1) {
                                    ss.append(byEnterpriseCode.getId().toString() + ",");
                                } else {
                                    ss.append(byEnterpriseCode.getId().toString());
                                }
                            }
                        }
                        drug.setDrugsEnterpriseIds(ss.toString());
                    }
                } catch (Exception e) {
                    LOGGER.error("配送药企有误 ," + e.getMessage(), e);
                }

                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(23)))) {
                        IUsingRateService bean = AppContextHolder.getBean("basic.usingRateService", IUsingRateService.class);
                        List<UsingRateDTO> allusingRateByOrganId = bean.findAllusingRateByOrganId(organId);
                        if (ObjectUtils.isEmpty(allusingRateByOrganId)) {
                            UsingRateDTO usingRateDTO = bean.findUsingRateDTOByOrganAndKey(0, getStrFromCell(row.getCell(23)));
                            if (ObjectUtils.isEmpty(usingRateDTO)) {
                                errMsg.append("平台未找到该用药频次").append(";");
                            } else {
                                drug.setUsingRateId(usingRateDTO.getId().toString());
                            }

                        } else {
                            UsingRateDTO usingRateDTOByOrganAndKey = bean.findUsingRateDTOByOrganAndKey(organId, getStrFromCell(row.getCell(23)));
                            if (ObjectUtils.isEmpty(usingRateDTOByOrganAndKey)) {
                                errMsg.append("机构未找到该用药频次").append(";");
                            } else {
                                drug.setUsingRateId(usingRateDTOByOrganAndKey.getId().toString());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("用药频次有误 ," + e.getMessage(), e);
                    errMsg.append("用药频次有误").append(";");
                }

                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(25)))) {
                        IUsePathwaysService bean = AppContextHolder.getBean("basic.usePathwaysService", IUsePathwaysService.class);
                        List<UsePathwaysDTO> allUsePathwaysByOrganId = bean.findAllUsePathwaysByOrganId(organId);
                        if (ObjectUtils.isEmpty(allUsePathwaysByOrganId)) {
                            UsePathwaysDTO usePathwaysDTO = bean.findUsePathwaysByOrganAndKey(0, getStrFromCell(row.getCell(25)));
                            if (ObjectUtils.isEmpty(usePathwaysDTO)) {
                                errMsg.append("平台未找到该用药途径").append(";");
                            } else {
                                drug.setUsePathwaysId(usePathwaysDTO.getId().toString());
                            }

                        } else {
                            UsePathwaysDTO usePathwaysDTO = bean.findUsePathwaysByOrganAndKey(organId, getStrFromCell(row.getCell(25)));
                            if (ObjectUtils.isEmpty(usePathwaysDTO)) {
                                errMsg.append("机构未找到该用药途径").append(";");
                            } else {
                                drug.setUsePathwaysId(usePathwaysDTO.getId().toString());
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("用药途径有误 ," + e.getMessage(), e);
                    errMsg.append("用药途径有误").append(";");
                }


                try {
                    if (!StringUtils.isEmpty(getStrFromCell(row.getCell(27)))) {
                        if ((new Integer(3).equals(drug.getDrugType()))) {
                            DrugEntrust byOrganIdAndDrugEntrustName = drugEntrustDAO.getByOrganIdAndDrugEntrustName(organId, getStrFromCell(row.getCell(27)));
                            if (byOrganIdAndDrugEntrustName != null) {
                                drug.setDrugEntrust(byOrganIdAndDrugEntrustName.getDrugEntrustName().toString());
                            } else {
                                errMsg.append("中药药品字典未找到该嘱托").append(";");
                            }
                        } else {
                            drug.setDrugEntrust(getStrFromCell(row.getCell(27)));
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("药品嘱托有误 ," + e.getMessage(), e);
                    errMsg.append("药品嘱托有误").append(";");
                }
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(28)))) {
                        drug.setIndicationsDeclare(getStrFromCell(row.getCell(28)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品适应症说明有误 ," + e.getMessage(), e);
                    errMsg.append("药品适应症说明有误").append(";");
                }
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(29)))) {
                        drug.setPackingMaterials(getStrFromCell(row.getCell(29)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品包装材料有误 ," + e.getMessage(), e);
                    errMsg.append("药品包装材料有误").append(";");
                }

                //中药不需要设置
                if (!(new Integer(3).equals(drug.getDrugType()))) {
                    try {
                        if (("是").equals(getStrFromCell(row.getCell(30)))) {
                            drug.setBaseDrug(1);
                        } else if (("否").equals(getStrFromCell(row.getCell(30)))) {
                            drug.setBaseDrug(0);
                        } else {
                            errMsg.append("是否基药格式不正确").append(";");
                        }

                    } catch (Exception e) {
                        LOGGER.error("是否基药有误 ," + e.getMessage(), e);
                        errMsg.append("是否基药有误").append(";");
                    }
                }
                try {
                    if (!StringUtils.isEmpty(getStrFromCell(row.getCell(31)))) {
                        if (("是").equals(getStrFromCell(row.getCell(31)))) {
                            drug.setMedicalInsuranceControl(true);
                        } else if (("否").equals(getStrFromCell(row.getCell(31)))) {
                            drug.setMedicalInsuranceControl(false);
                        } else {
                            errMsg.append("医保控制格式错误").append(";");
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("医保控制有误 ," + e.getMessage(), e);
                    errMsg.append("医保控制有误").append(";");
                }

                try {
                    if (!StringUtils.isEmpty(getStrFromCell(row.getCell(32)))) {
                        if (("单复方可报").equals(getStrFromCell(row.getCell(32)))) {
                            drug.setUnilateralCompound(0);
                        } else if (("单方不可报，复方可报").equals(getStrFromCell(row.getCell(32)))) {
                            drug.setUnilateralCompound(1);
                        } else if (("单复方均不可报").equals(getStrFromCell(row.getCell(32)))) {
                            drug.setUnilateralCompound(2);
                        } else {
                            errMsg.append("单复方格式错误").append(";");
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("单复方有误 ," + e.getMessage(), e);
                    errMsg.append("单复方有误").append(";");
                }

                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(33)))) {
                        drug.setMedicalDrugCode(getStrFromCell(row.getCell(33)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品医保药品编码有误 ," + e.getMessage(), e);
                    errMsg.append("药品医保药品编码有误").append(";");
                }
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(34)))) {
                        drug.setMedicalDrugFormCode(getStrFromCell(row.getCell(34)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品医保剂型代码有误 ," + e.getMessage(), e);
                    errMsg.append("药品医保剂型代码有误").append(";");
                }
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(35)))) {
                        drug.setHisFormCode(getStrFromCell(row.getCell(35)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品HIS剂型代码有误 ," + e.getMessage(), e);
                    errMsg.append("药品HIS剂型代码有误").append(";");
                }
                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(36)))) {
                        drug.setLicenseNumber(getStrFromCell(row.getCell(36)));
                    }
                } catch (Exception e) {
                    LOGGER.error("药品国药准字有误 ," + e.getMessage(), e);
                    errMsg.append("药品国药准字有误").append(";");
                }
                try {
                    if (!StringUtils.isEmpty(getStrFromCell(row.getCell(37)))) {
                        String strFromCell = getStrFromCell(row.getCell(37));
                        StringBuilder ss = new StringBuilder();
                        String[] split = strFromCell.split(",");
                        for (int i = 0; i < split.length; i++) {
                            String applyBusiness = "";
                            if ("药品处方".equals(split[i])) {
                                applyBusiness = "1";
                            } else if ("诊疗处方".equals(split[i])) {
                                applyBusiness = "2";
                            } else {
                                break;
                            }
                            if (i != split.length - 1) {
                                ss.append(applyBusiness + ",");
                            } else {
                                ss.append(applyBusiness);
                            }
                        }
                        drug.setApplyBusiness(ss.toString());
                    } else {
                        errMsg.append("【适用业务】未填写").append(";");
                    }
                } catch (Exception e) {
                    LOGGER.error("适用业务有误," + e.getMessage(), e);
                    errMsg.append("适用业务有误").append(";");
                }

                try {
                    if (StringUtils.isNotEmpty(getStrFromCell(row.getCell(38)))) {
                        if (("是").equals(getStrFromCell(row.getCell(38)))) {
                            drug.setTargetedDrugType(1);
                        } else if (("否").equals(getStrFromCell(row.getCell(38)))) {
                            drug.setTargetedDrugType(0);
                        } else {
                            errMsg.append("是否靶向药格式错误").append(";");
                        }
                    }
                } catch (Exception e) {
                    LOGGER.error("是否靶向药有误 ," + e.getMessage(), e);
                    errMsg.append("是否靶向药有误").append(";");
                }

                try {
                    if(StringUtils.isNotEmpty(getStrFromCell(row.getCell(39)))){
                        drug.setSmallestSaleMultiple(Integer.parseInt(getStrFromCell(row.getCell(39)).trim()));
                    }
                } catch (Exception e) {
                    LOGGER.error("SmallestSaleMultiple ," + e.getMessage(), e);
                    errMsg.append("SmallestSaleMultiple").append(";");
                }

                if (!ObjectUtils.isEmpty(organId)) {
                    DrugSourcesDAO dao = DAOFactory.getDAO(DrugSourcesDAO.class);
                    List<DrugSources> byDrugSourcesId = dao.findByDrugSourcesId(organId);
                    if (ObjectUtils.isEmpty(byDrugSourcesId)) {
                        OrganService bean = AppDomainContext.getBean("basic.organService", OrganService.class);
                        OrganDTO byOrganId = bean.getByOrganId(organId);
                        DrugSources saveData = new DrugSources();
                        saveData.setDrugSourcesId(byOrganId.getOrganId());
                        saveData.setDrugSourcesName(byOrganId.getName());
                        DrugSources save = dao.save(saveData);
                        drug.setSourceOrgan(save.getDrugSourcesId());
                    } else {
                        drug.setSourceOrgan(organId);
                    }
                }
                drug.setStatus(DrugMatchConstant.UNMATCH);
                drug.setOperator(operator);
                drug.setDrugSource(0);
                if (errMsg.length() > 1) {
                    int showNum = rowIndex + 1;
                    String error = ("【第" + showNum + "行】" + errMsg.substring(0, errMsg.length() - 1) + "\n");
                    errDrugListMatchList.add(error);
                    failNum++;
                } else {
                    try {
                        AutoMatch(drug);
                        boolean isSuccess = drugListMatchDAO.updateData(drug);
                        if (!isSuccess) {
                            //自动匹配功能暂无法提供
                            DrugListMatch save = drugListMatchDAO.save(drug);
                            try {
                                automaticDrugMatch(save, operator);
                            } catch (Exception e) {
                                LOGGER.error("readDrugExcel.updateMatchAutomatic fail,", e);
                            }
                            addNum++;
                        } else {
                            List<DrugListMatch> dataByOrganDrugCode = drugListMatchDAO.findDataByOrganDrugCodenew(drug.getOrganDrugCode(), drug.getSourceOrgan());
                            if (dataByOrganDrugCode != null && dataByOrganDrugCode.size() > 0) {
                                for (DrugListMatch drugListMatch : dataByOrganDrugCode) {
                                    try {
                                        automaticDrugMatch(drugListMatch, operator);
                                        drugListMatch.setStatus(DrugMatchConstant.ALREADY_MATCH);
                                        drugListMatchDAO.updateData(drugListMatch);
                                    } catch (Exception e) {
                                        LOGGER.error("readDrugExcel.updateMatchAutomatic fail,", e);
                                    }
                                }
                            }
                            updateNum++;
                        }
                    } catch (Exception e) {
                        LOGGER.error("save or update drugListMatch error " + e.getMessage(), e);
                    }
                }
            }
        }
        LOGGER.info("机构药品目录导入数据校验errorMsg:{},文件名={},organId={},operator={}", JSONUtils.toString(errDrugListMatchList),originalFilename, organId, operator);
        LOGGER.info("机构药品目录导入数据校验结束,文件名={},organId={},operator={}", originalFilename, organId, operator);
        //导入药品记录
        ImportDrugRecord importDrugRecord = new ImportDrugRecord();
        importDrugRecord.setFileName(originalFilename);
        importDrugRecord.setOrganId(organId);
        importDrugRecord.setAddNum(addNum);
        importDrugRecord.setUpdateNum(updateNum);
        importDrugRecord.setFailNum(failNum);
        importDrugRecord.setImportOperator(operator);
        if (errDrugListMatchList.size() > 0) {
            result.put("code", 609);
            result.put("msg", errDrugListMatchList);
            result.put("addNum", addNum);
            result.put("updateNum", updateNum);
            result.put("failNum", failNum);
            importDrugRecord.setErrMsg(JSONUtils.toString(errDrugListMatchList.stream().limit(600).collect(Collectors.toList())));
            importDrugRecordDAO.save(importDrugRecord);
            return result;
        }
        importDrugRecordDAO.save(importDrugRecord);

        result.put("addNum", addNum);
        result.put("updateNum", updateNum);
        result.put("failNum", total - addNum - updateNum - bankNumber);
        LOGGER.info(operator + "结束 readDrugExcel 方法" + System.currentTimeMillis() + "当前进程=" + Thread.currentThread().getName());
        result.put("code", 200);
        LOGGER.info("DrugToolService result={}",JSONUtils.toString(result));
        return result;
    }


    public void AutoMatch(DrugListMatch drug) {
        DrugList drugList = null;
        String addrArea = null;
        ProvinceDrugList provinceDrugList = null;
        if (StringUtils.isNotEmpty(drug.getRegulationDrugCode()) || drug.getPlatformDrugId() != null) {
            drugList = drugListDAO.findValidByDrugId(drug.getPlatformDrugId());
            // 如果该机构有省平台关联的话
            if (checkOrganRegulation(drug.getSourceOrgan()) && isHaveReulationId(drug.getSourceOrgan())) {
                addrArea = checkOrganAddrArea(drug.getSourceOrgan());
                provinceDrugList = provinceDrugListDAO.getByProvinceIdAndDrugId(addrArea, drug.getRegulationDrugCode(), 1);
                if (drugList != null) {
                    // 以匹配
                    drug.setStatus(DrugMatchConstant.ALREADY_MATCH);
                    drug.setMatchDrugId(drugList.getDrugId());
                } else {
                    //未匹配
                    drug.setStatus(DrugMatchConstant.UNMATCH);
                    drug.setPlatformDrugId(null);
                }
            } else {
                if (drugList != null) {
                    drug.setStatus(DrugMatchConstant.ALREADY_MATCH);
                    drug.setMatchDrugId(drugList.getDrugId());
                } else {
                    drug.setPlatformDrugId(null);
                }
            }
        }
    }

    /**
     * 获取单元格值（字符串）
     *
     * @param cell
     * @return
     */
    private String getStrFromCell(Cell cell) {
        if (cell == null) {
            return null;
        }
        //读取数据前设置单元格类型
        cell.setCellType(CellType.STRING);
        String strCell = cell.getStringCellValue();
        if (strCell != null) {
            strCell = strCell.trim();
            if (StringUtils.isEmpty(strCell)) {
                strCell = null;
            }
        }
        return strCell;
    }

    /**
     * 判断该机构是否已导入过
     */
    @RpcService
    public boolean isOrganImported(int organId) {
        boolean isImported = true;
        List<DrugListMatch> drugLists = drugListMatchDAO.findMatchDataByOrgan(organId);
        if (CollectionUtils.isEmpty(drugLists)) {
            isImported = false;
        }
        return isImported;
    }

    /**
     * 获取或刷新临时药品数据
     */
    @RpcService
    public QueryResult<DrugListMatch> findData(int organId, int start, int limit) {
        int status = 2;
        return drugListMatchDAO.findMatchDataByOrgan(organId, start, limit, status);
    }

    /**
     * 更新无匹配数据
     */
    @RpcService
    public DrugListMatch updateNoMatchData(int drugId, String operator) {
        if (StringUtils.isEmpty(operator)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "operator is required");
        }

        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);

        List<DrugList> drugLists = drugListDAO.findRepeatDrugList(drugListMatch.getDrugName(), drugListMatch.getSaleName(), drugListMatch.getDrugType(), drugListMatch.getProducer(), drugListMatch.getDrugSpec(), drugListMatch.getSourceOrgan());
        if (CollectionUtils.isNotEmpty(drugLists)) {
            throw new DAOException(DAOException.VALIDATE_FALIED, "此药品已经存在，对应药品为【" + drugLists.get(0).getDrugCode() + "】【" + drugListMatch.getDrugName() + "】，请勿重复添加。");
        }

        //如果是已匹配的取消匹配
        if (drugListMatch.getStatus().equals(DrugMatchConstant.ALREADY_MATCH)) {
            drugListMatchDAO.updateDrugListMatchInfoById(drugId, ImmutableMap.of("status", DrugMatchConstant.UNMATCH, "operator", operator));
        }
        IBusActionLogService busActionLogService = AppDomainContext.getBean("opbase.busActionLogService", IBusActionLogService.class);
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, ImmutableMap.of("isNew", 1, "status", DrugMatchConstant.MARKED, "operator", operator));
        LOGGER.info("updateNoMatchData 操作人->{}更新无匹配数据,drugId={};status ->before={},after=3", operator, drugId, drugListMatch.getStatus());
        //updata by maoly on 2020/03/16 自动同步至平台药品库
        DrugList drugList = new DrugList();
        //药品名
        drugList.setDrugName(drugListMatch.getDrugName());
        //商品名
        drugList.setSaleName(drugListMatch.getSaleName());
        //一次剂量
        drugList.setUseDose(drugListMatch.getUseDose());
        //剂量单位
        drugList.setUseDoseUnit(drugListMatch.getUseDoseUnit());
        //规格
        drugList.setDrugSpec(drugListMatch.getDrugSpec());
        //药品包装数量---默认1
        drugList.setPack(drugListMatch.getPack() == null ? 1 : drugListMatch.getPack());
        //药品单位
        drugList.setUnit(drugListMatch.getUnit());
        //药品类型
        drugList.setDrugType(drugListMatch.getDrugType());
        //剂型
        drugList.setDrugForm(drugListMatch.getDrugForm());
        drugList.setPrice1(drugListMatch.getPrice().doubleValue());
        drugList.setPrice2(drugListMatch.getPrice().doubleValue());
        //厂家
        drugList.setProducer(drugListMatch.getProducer());
        //其他
        drugList.setDrugClass("1901");
        drugList.setAllPyCode("");
        drugList.setStatus(1);
        drugList.setCreateDt(new Date());
        drugList.setLastModify(new Date());
        //来源机构
        drugList.setSourceOrgan(drugListMatch.getSourceOrgan());
        if (drugListMatch.getSourceOrgan() != null) {
            DrugSourcesDAO dao = DAOFactory.getDAO(DrugSourcesDAO.class);
            DrugSources drugSources = dao.get(drugListMatch.getSourceOrgan());
            if (drugSources == null) {
                OrganService bean = AppDomainContext.getBean("basic.organService", OrganService.class);
                OrganDTO byOrganId = bean.getByOrganId(drugListMatch.getSourceOrgan());
                DrugSources saveData = new DrugSources();
                saveData.setDrugSourcesId(byOrganId.getOrganId());
                saveData.setDrugSourcesName(byOrganId.getName());
                dao.save(saveData);
            }
        }
        Integer status = drugListMatch.getStatus();
        try {
            DrugList save = drugListDAO.save(drugList);
            busActionLogService.recordBusinessLogRpcNew("通用药品管理", "", "DrugList", "(药品小工具)新增通用药品【" + save.getDrugId() + "-" + save.getDrugName() + "】", "平台");
            if (save != null) {
                drugListDAO.updateDrugListInfoById(save.getDrugId(), ImmutableMap.of("drugCode", String.valueOf(save.getDrugId())));
                //更新为已匹配，将已标记上传的药品自动关联上
                //判断更新成已匹配还是匹配中
                if (checkOrganRegulation(drugListMatch.getSourceOrgan()) && isHaveReulationId(drugListMatch.getSourceOrgan()) && StringUtils.isEmpty(drugListMatch.getRegulationDrugCode())) {
                    //匹配中
                    status = DrugMatchConstant.MATCHING;
                    drugListMatch.setStatus(status);
                } else {
                    //已匹配
                    status = DrugMatchConstant.ALREADY_MATCH;
                    drugListMatch.setStatus(status);
                }
                drugListMatch.setPlatformDrugId(save.getDrugId());
                drugListMatchDAO.updatePlatformDrugIdByDrugId(save.getDrugId(), drugListMatch.getDrugId());
                drugListMatchDAO.updateDrugListMatchInfoById(drugListMatch.getDrugId(), ImmutableMap.of("status", status, "matchDrugId", save.getDrugId()));
            }
        } catch (Exception e) {
            LOGGER.error("DrugToolService.updateNoMatchData fail,", e);
            throw new DAOException(609, "数据自动导入平台药品库失败!");
        }

        return drugListMatch;
    }

    /**
     * 取消已匹配状态和已提交状态
     */
    @RpcService
    public void cancelMatchStatus(int drugId, String operator) {
        if (StringUtils.isEmpty(operator)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "operator is required");
        }
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, ImmutableMap.of("status", DrugMatchConstant.UNMATCH, "operator", operator));
        LOGGER.info("cancelMatchStatus 操作人->{}更新为未匹配状态,drugId={};status ->before={},after=0", operator, drugId, drugListMatch.getStatus());
    }

    /**
     * 更新已匹配状态(未匹配0，已匹配1，已提交2,已标记3)
     */
    @RpcService
    public void updateMatchStatus(int drugId, int matchDrugId, String operator) {
        if (StringUtils.isEmpty(operator)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "operator is required");
        }
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, ImmutableMap.of("status", DrugMatchConstant.ALREADY_MATCH, "matchDrugId", matchDrugId, "operator", operator));
        LOGGER.info("updateMatchStatus 操作人->{}更新已匹配状态,drugId={};status ->before={},after=1", operator, drugId, drugListMatch.getStatus());
    }

    /**
     * 查找能匹配的机构
     */
    @RpcService
    public List<OrganDTO> findOrgan() {
        return organService.findOrgans();
    }

    /**
     * 关键字模糊匹配机构
     */
    @RpcService
    public List<OrganDTO> findOrganLikeShortName(String shortName) {
        return organService.findOrganLikeShortName(shortName);
    }

    /**
     * 查询所有机构并封装返回参数给前端，存入前端缓存使用
     */
    @RpcService
    public List<OrganToolBean> findOrganByRecipeTools() {
        LOGGER.info("findOrganByRecipeTools start");
        List<OrganToolBean> toollist = new ArrayList<>();
        try {
            List<OrganDTO> organDTOList = organService.findOrganLikeShortName("");
            for (OrganDTO o : organDTOList) {
                OrganToolBean toolBean = new OrganToolBean();
                toolBean.setName(o.getName());
                toolBean.setOrganId(o.getOrganId());
                toolBean.setPyCode(o.getPyCode());
                toolBean.setShortName(o.getShortName());
                toolBean.setWxAccount(o.getWxAccount());
                toollist.add(toolBean);
            }
        } catch (Exception e) {
            LOGGER.error("findOrganByRecipeTools 药品小工具查询所有机构接口异常", e);
            e.printStackTrace();
        }
        return toollist;
    }

    /**
     * 搜索当前用户的历史搜索记录
     *
     * @param userkey 搜索人的唯一标识
     * @return
     * @throws InterruptedException
     */
    @RpcService
    public List<?> findOrganSearchHistoryRecord(String userkey) {
        LOGGER.info("findOrganSearchHistoryRecord =userkey={}==", userkey);
        //创建一个存储容量为10的ArrayBlockingQueue对列
        ArrayBlockingQueue queue = new ArrayBlockingQueue(10);
        List<Object> listCmap = new ArrayList<>();
        //存在历史搜索记录
        if (cmap.get(userkey) != null && cmap.get(userkey).size() > 0) {
            queue = cmap.get(userkey);
            Object[] arrayQueue = queue.toArray();
            for (Object s : arrayQueue) {
                listCmap.add(s);
            }
        }
        LOGGER.info("findOrganSearchHistoryRecord HistoryRecord  queue{}==", queue.size());
        return listCmap;
    }

    /**
     * 保存搜索人的历史记录，在导入药品库确定时调用
     *
     * @param shortName 搜索内容
     * @param userkey   搜索人的唯一标识
     * @return
     * @throws InterruptedException
     */
    @RpcService
    public void saveShortNameRecord(String shortName, String organId, String userkey) throws InterruptedException {
        LOGGER.info("saveShortNameRecord shortName=={}==organId=={}==userkey={}==", shortName, organId, userkey);
        //创建一个存储容量为10的ArrayBlockingQueue对列
        ArrayBlockingQueue queue = new ArrayBlockingQueue(10);
        OrganToolBean ort = new OrganToolBean();
        //当搜索框为空的情况，直接返回缓存中的历史记录数据
        if (!StringUtils.isEmpty(shortName)) {
            if (cmap.get(userkey) != null && cmap.get(userkey).size() > 0) {
                queue = cmap.get(userkey);
            }

            ort.setOrganId(Integer.parseInt(organId));
            ort.setName(shortName);

            Object[] arrayQueue = queue.toArray();
            for (Object s : arrayQueue) {
                OrganToolBean t = (OrganToolBean) s;
                //通过organId过滤
                if (t.getOrganId() == Integer.parseInt(organId)) {
                    queue.remove(s);
                    break;
                }
            }
            //当容量超过10个时，取出第一个元素并删除
            if (10 == queue.size()) {
                queue.poll();
            }
            queue.put(ort);

            cmap.put(userkey, queue);
            LOGGER.info("saveShortNameRecord HistoryRecord  cmap{}==", cmap);
        }

    }


    /**
     * 药品匹配
     */
    @RpcService
    public List<DrugListBean> drugMatch(int drugMacthId) {
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugMacthId);

        String str = DrugMatchUtil.match(drugListMatch.getDrugName());
        //根据药品名取标准药品库查询相关药品
        List<DrugList> drugLists = null;
        List<DrugListBean> drugListBeans = null;
        drugListBeans = drugMatchSearch(drugMacthId, drugListMatch.getSourceOrgan(), drugListMatch.getDrugName(), drugListMatch.getProducer());
        //已匹配状态返回匹配药品id
        if (CollectionUtils.isEmpty(drugListBeans)) {
            try {
                drugLists = drugListCache.get(str);
            } catch (ExecutionException e) {
                LOGGER.error("drugMatch:" + e.getMessage(), e);
            }
            if (CollectionUtils.isNotEmpty(drugLists)) {
                drugListBeans = ObjectCopyUtils.convert(drugLists, DrugListBean.class);
                if (drugListMatch.getStatus().equals(DrugMatchConstant.ALREADY_MATCH) || drugListMatch.getStatus().equals(DrugMatchConstant.SUBMITED) || drugListMatch.getStatus().equals(DrugMatchConstant.MATCHING)) {
                    for (DrugListBean drugListBean : drugListBeans) {
                        if (drugListBean.getDrugId().equals(drugListMatch.getMatchDrugId())) {
                            drugListBean.setIsMatched(true);
                        }
                    }
                }
            }
        } else {
            if (drugListMatch.getStatus().equals(DrugMatchConstant.ALREADY_MATCH) || drugListMatch.getStatus().equals(DrugMatchConstant.SUBMITED) || drugListMatch.getStatus().equals(DrugMatchConstant.MATCHING)) {
                for (DrugListBean drugListBean : drugListBeans) {
                    if (drugListBean.getDrugId().equals(drugListMatch.getMatchDrugId())) {
                        drugListBean.setIsMatched(true);
                    }
                }

            }
        }
        return drugListBeans;
    }

    /**
     * 药品匹配 搜索专用
     */
    @RpcService
    public List<DrugListBean> drugMatchSearch(int drugId, Integer organId, final String input, final String producer) {
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);
        //根据药品名取标准药品库查询相关药品
        List<DrugList> drugLists = null;
        List<DrugListBean> drugListBeans = null;
        drugLists = drugListDAO.findBySaleNameLikeSearch(organId, input, producer);
        //已匹配状态返回匹配药品id
        if (CollectionUtils.isNotEmpty(drugLists)) {
            drugListBeans = ObjectCopyUtils.convert(drugLists, DrugListBean.class);
            if (drugListMatch.getStatus().equals(DrugMatchConstant.ALREADY_MATCH) || drugListMatch.getStatus().equals(DrugMatchConstant.SUBMITED) || drugListMatch.getStatus().equals(DrugMatchConstant.MATCHING)) {
                for (DrugListBean drugListBean : drugListBeans) {
                    if (drugListBean.getDrugId().equals(drugListMatch.getMatchDrugId())) {
                        drugListBean.setIsMatched(true);
                    }
                }
            }
        }
        return drugListBeans;

    }

    /**
     * 药品提交至organDrugList(将匹配完成的数据提交更新)人工提交
     */
    @RpcService
    public Map<String, Integer> drugManualCommit(int organId, int status) {
        List<DrugListMatch> matchDataByOrgan = drugListMatchDAO.findMatchDataByOrganAndStatus(organId);
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<DrugListMatch> lists = drugListMatchDAO.findDataByOrganAndStatus(organId, status);
                int num = 0;
                //更新数据到organDrugList并更新状态已提交
                for (DrugListMatch drugListMatch : lists) {
                    if (drugListMatch.getMatchDrugId() != null) {
                        OrganDrugList organDrugList = new OrganDrugList();
                        organDrugList.setDrugId(drugListMatch.getMatchDrugId());
                        organDrugList.setOrganDrugCode(drugListMatch.getOrganDrugCode());
                        organDrugList.setOrganId(drugListMatch.getSourceOrgan());
                        if (drugListMatch.getPrice() == null) {
                            organDrugList.setSalePrice(new BigDecimal(0));
                        } else {
                            organDrugList.setSalePrice(drugListMatch.getPrice());
                        }
                        organDrugList.setDrugName(drugListMatch.getDrugName());
                        if (StringUtils.isEmpty(drugListMatch.getSaleName())) {
                            organDrugList.setSaleName(drugListMatch.getDrugName());
                        } else {
                            if (drugListMatch.getSaleName().equals(drugListMatch.getDrugName())) {
                                organDrugList.setSaleName(drugListMatch.getSaleName());
                            } else {
                                organDrugList.setSaleName(drugListMatch.getSaleName() + " " + drugListMatch.getDrugName());
                            }

                        }


                        organDrugList.setUsingRate(drugListMatch.getUsingRate());
                        organDrugList.setUsePathways(drugListMatch.getUsePathways());
                        if (ObjectUtils.isEmpty(drugListMatch.getProducer())) {
                            organDrugList.setProducer("");
                        } else {
                            organDrugList.setProducer(drugListMatch.getProducer());
                        }
                        organDrugList.setUseDose(drugListMatch.getUseDose());
                        organDrugList.setRecommendedUseDose(drugListMatch.getDefaultUseDose());
                        organDrugList.setPack(drugListMatch.getPack());
                        organDrugList.setUnit(drugListMatch.getUnit());
                        organDrugList.setUseDoseUnit(drugListMatch.getUseDoseUnit());
                        organDrugList.setDrugSpec(drugListMatch.getDrugSpec());
                        organDrugList.setRetrievalCode(drugListMatch.getRetrievalCode());
                        organDrugList.setDrugForm(drugListMatch.getDrugForm());
                        organDrugList.setBaseDrug(drugListMatch.getBaseDrug());
                        organDrugList.setRegulationDrugCode(drugListMatch.getRegulationDrugCode());
                        organDrugList.setLicenseNumber(drugListMatch.getLicenseNumber());
                        organDrugList.setPharmacyName(drugListMatch.getPharmacy());
                        organDrugList.setChemicalName(drugListMatch.getChemicalName());
                        organDrugList.setPackingMaterials(drugListMatch.getPackingMaterials());
                        organDrugList.setTakeMedicine(0);
                        organDrugList.setStatus(1);
                        organDrugList.setProducerCode("");
                        organDrugList.setLastModify(new Date());
                        if (StringUtils.isNotEmpty(drugListMatch.getDrugManfCode())) {
                            organDrugList.setProducerCode(drugListMatch.getDrugManfCode());
                        }
                        organDrugList.setMedicalDrugCode(drugListMatch.getMedicalDrugCode());
                        organDrugList.setMedicalDrugFormCode(drugListMatch.getMedicalDrugFormCode());
                        organDrugList.setDrugFormCode(drugListMatch.getHisFormCode());
                        organDrugList.setUsingRateId(drugListMatch.getUsingRateId());
                        organDrugList.setUsePathwaysId(drugListMatch.getUsePathwaysId());
                        organDrugList.setSmallestUnitUseDose(drugListMatch.getSmallestUnitUseDose());
                        organDrugList.setRecommendedUseDose(drugListMatch.getRecommendedUseDose());
                        organDrugList.setDrugsEnterpriseIds(drugListMatch.getDrugsEnterpriseIds());
                        organDrugList.setUseDoseSmallestUnit(drugListMatch.getUseDoseSmallestUnit());
                        organDrugList.setUnilateralCompound(drugListMatch.getUnilateralCompound());
                        if (!ObjectUtils.isEmpty(drugListMatch.getApplyBusiness())) {
                            organDrugList.setApplyBusiness(drugListMatch.getApplyBusiness());
                        } else {
                            organDrugList.setApplyBusiness("1");
                        }

                        Boolean isSuccess = organDrugListDAO.updateData(organDrugList);
                        if (!isSuccess) {
                            OrganDrugList save = organDrugListDAO.save(organDrugList);
                            organDrugSync(save);
                            //同步药品到监管备案
                            RecipeBusiThreadPool.submit(() -> {
                                organDrugListService.uploadDrugToRegulation(organDrugList);
                                return null;
                            });
                            num = num + 1;
                        } else {
                            List<OrganDrugList> byDrugIdAndOrganId = organDrugListDAO.findByOrganDrugCodeAndOrganId(organDrugList.getOrganDrugCode(), organDrugList.getOrganId());
                            if (byDrugIdAndOrganId != null && byDrugIdAndOrganId.size() > 0) {
                                for (OrganDrugList drugList : byDrugIdAndOrganId) {
                                    organDrugSync(drugList);
                                }
                            }
                        }
                    }
                }
                setResult(num);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        Map<String, Integer> result = Maps.newHashMap();
        result.put("before", matchDataByOrgan.size());
        result.put("saveSuccess", action.getResult());
        LOGGER.info("drugManualCommit success  beforeNum= " + matchDataByOrgan.size() + "saveSuccessNum=" + action.getResult());
        return result;
    }

    /**
     * 药品提交(将匹配完成的数据提交更新)----互联网六期改为人工提交
     */
    @RpcService(timeout = 120)
    public Map<String, Integer> drugCommit(List<DrugListMatch> lists, Integer organ) {
        List<DrugListMatch> lists1 = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();
        Integer result = 0;
        try {
            if (CollectionUtils.isEmpty(lists)) {
                lists = drugListMatchDAO.findMatchDataByOrganAndStatus(organ);
            }
            if (lists.size() > 0) {
                for (DrugListMatch drugListMatch : lists) {
                    DrugListMatch db = drugListMatchDAO.get(drugListMatch.getDrugId());
                    if (1 == db.getStatus()) {
                        db.setUsingRate(drugListMatch.getUsingRate());
                        db.setUsePathways(drugListMatch.getUsePathways());
                        db.setDefaultUseDose(drugListMatch.getDefaultUseDose());
                        db.setStatus(DrugMatchConstant.SUBMITED);
                        lists1.add(db);
                        drugListMatchDAO.update(db);
                    }
                }
                if (lists1.size() > 0) {
                    List<OrganDrugList> organDrugLists = this.drugManualCommitNew(lists1);
                    result = organDrugLists.size();
                    //同步药品到监管备案
                    RecipeBusiThreadPool.submit(() -> {
                        if (!ObjectUtils.isEmpty(organDrugLists)) {
                            for (OrganDrugList organDrugList : organDrugLists) {
                                organDrugListService.uploadDrugToRegulation(organDrugList);
                            }
                        }
                        return null;
                    });
                }
                map.put("successCount", result);
            }

        } catch (Exception e) {
            LOGGER.error("DrugToolService.drugCommit fail", e);
            throw new DAOException(609, "药品数据自动导入机构药品库失败！");
        }
        return map;

    }

    /**
     * 药品提交(将匹配完成的数据提交更新)----药品手动同步调用
     */
    @RpcService
    public Map<String, Integer> drugCommitT(List<DrugListMatch> lists, Integer organ) {
        List<DrugListMatch> lists1 = new ArrayList<>();
        Map<String, Integer> map = new HashMap<>();
        Integer result = 0;
        try {
            if (CollectionUtils.isEmpty(lists)) {
                lists = drugListMatchDAO.findMatchDataByOrganAndStatusAndrugSource(organ);
            }
            if (lists.size() > 0) {
                for (DrugListMatch drugListMatch : lists) {
                    DrugListMatch db = drugListMatchDAO.get(drugListMatch.getDrugId());
                    if (1 == db.getStatus()) {
                        db.setUsingRate(drugListMatch.getUsingRate());
                        db.setUsePathways(drugListMatch.getUsePathways());
                        db.setDefaultUseDose(drugListMatch.getDefaultUseDose());
                        db.setStatus(DrugMatchConstant.SUBMITED);
                        lists1.add(db);
                        drugListMatchDAO.update(db);
                    }
                }
                if (lists1.size() > 0) {
                    List<OrganDrugList> organDrugLists = this.drugManualCommitNew(lists1);
                    result = organDrugLists.size();
                    //同步药品到监管备案
                    RecipeBusiThreadPool.submit(() -> {
                        if (!ObjectUtils.isEmpty(organDrugLists)) {
                            for (OrganDrugList organDrugList : organDrugLists) {
                                organDrugListService.uploadDrugToRegulation(organDrugList);
                            }
                        }
                        return null;
                    });
                }
                map.put("successCount", result);
            }

        } catch (Exception e) {
            LOGGER.error("DrugToolService.drugCommit fail", e);
            throw new DAOException(609, "药品数据自动导入机构药品库失败！");
        }
        return map;

    }

    private List<OrganDrugList> drugManualCommitNew(List<DrugListMatch> lists) {
        IBusActionLogService busActionLogService = AppDomainContext.getBean("opbase.busActionLogService", IBusActionLogService.class);
        DrugListMatch drugListMatch = lists.get(0);
        OrganService organService = BasicAPI.getService(OrganService.class);
        OrganDTO organDTO = organService.getByOrganId(drugListMatch.getSourceOrgan());
        //【机构名称】批量新增药品【编码-药品名】……
        StringBuilder saveMsg = new StringBuilder("【" + organDTO.getName() + "】批量新增药品");
        //【机构名称】更新药品【编码-药品名】……
        StringBuilder updateMsg = new StringBuilder("【" + organDTO.getName() + "】更新药品");
        final HibernateStatelessResultAction<List<OrganDrugList>> action = new AbstractHibernateStatelessResultAction<List<OrganDrugList>>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                int num = 0;
                List<OrganDrugList> organDrugLists = Lists.newArrayList();
                //更新数据到organDrugList并更新状态已提交
                for (DrugListMatch drugListMatch : lists) {
                    if (drugListMatch.getMatchDrugId() != null) {
                        OrganDrugList organDrugList = new OrganDrugList();
                        organDrugList.setDrugId(drugListMatch.getMatchDrugId());
                        organDrugList.setOrganDrugCode(drugListMatch.getOrganDrugCode());
                        organDrugList.setDrugItemCode(drugListMatch.getDrugItemCode());
                        organDrugList.setOrganId(drugListMatch.getSourceOrgan());
                        if (drugListMatch.getPrice() == null) {
                            organDrugList.setSalePrice(new BigDecimal(0));
                        } else {
                            organDrugList.setSalePrice(drugListMatch.getPrice());
                        }
                        if (drugListMatch.getDrugType() != null && drugListMatch.getDrugType() == 3 && drugListMatch.getPack() == null)
                            organDrugList.setPack(1);
                        organDrugList.setDrugName(drugListMatch.getDrugName());
                        if (StringUtils.isEmpty(drugListMatch.getSaleName())) {
                            organDrugList.setSaleName(drugListMatch.getDrugName());
                        } else {
                            organDrugList.setSaleName(drugListMatch.getSaleName());
                        }

                        organDrugList.setUsingRate(drugListMatch.getUsingRate());
                        organDrugList.setUsePathways(drugListMatch.getUsePathways());
                        if (ObjectUtils.isEmpty(drugListMatch.getProducer())) {
                            organDrugList.setProducer("");
                        } else {
                            organDrugList.setProducer(drugListMatch.getProducer());
                        }
                        organDrugList.setUseDose(drugListMatch.getUseDose());
                        organDrugList.setRecommendedUseDose(drugListMatch.getDefaultUseDose());
                        organDrugList.setPack(drugListMatch.getPack());
                        organDrugList.setUnit(drugListMatch.getUnit());
                        organDrugList.setUseDoseUnit(drugListMatch.getUseDoseUnit());
                        organDrugList.setDrugSpec(drugListMatch.getDrugSpec());
                        organDrugList.setRetrievalCode(drugListMatch.getRetrievalCode());
                        organDrugList.setDrugForm(drugListMatch.getDrugForm());
                        organDrugList.setChemicalName(drugListMatch.getChemicalName());
                        organDrugList.setBaseDrug(drugListMatch.getBaseDrug());
                        if (ObjectUtils.isEmpty(drugListMatch.getRegulationDrugCode())) {
                            IHisServiceConfigService configService = AppContextHolder.getBean("his.hisServiceConfig", IHisServiceConfigService.class);
                            Boolean regulationFlag = configService.getRegulationFlag();
                            if (regulationFlag) {
                                organDrugList.setRegulationDrugCode(organDrugList.getDrugId().toString());
                            } else {
                                organDrugList.setRegulationDrugCode(drugListMatch.getRegulationDrugCode());
                            }
                        } else {
                            organDrugList.setRegulationDrugCode(drugListMatch.getRegulationDrugCode());
                        }

                        organDrugList.setLicenseNumber(drugListMatch.getLicenseNumber());
                        organDrugList.setPharmacy(drugListMatch.getPharmacy());
                        organDrugList.setPackingMaterials(drugListMatch.getPackingMaterials());
                        organDrugList.setUseDoseSmallestUnit(drugListMatch.getUseDoseSmallestUnit());
                        organDrugList.setUnilateralCompound(drugListMatch.getUnilateralCompound());

                        organDrugList.setTakeMedicine(0);
                        organDrugList.setStatus(1);
                        organDrugList.setProducerCode("");
                        organDrugList.setCreateDt(drugListMatch.getCreateDt());
                        organDrugList.setLastModify(new Date());
                        if (StringUtils.isNotEmpty(drugListMatch.getDrugManfCode())) {
                            organDrugList.setProducerCode(drugListMatch.getDrugManfCode());
                        }
                        organDrugList.setMedicalDrugCode(drugListMatch.getMedicalDrugCode());
                        organDrugList.setMedicalDrugFormCode(drugListMatch.getMedicalDrugFormCode());
                        organDrugList.setDrugFormCode(drugListMatch.getHisFormCode());
                        organDrugList.setDrugEntrust(drugListMatch.getDrugEntrust());
                        if (!ObjectUtils.isEmpty(drugListMatch.getMedicalInsuranceControl())) {
                            organDrugList.setMedicalInsuranceControl(drugListMatch.getMedicalInsuranceControl());
                        } else {
                            organDrugList.setMedicalInsuranceControl(false);
                        }
                        organDrugList.setIndicationsDeclare(drugListMatch.getIndicationsDeclare());
                        organDrugList.setSupportDownloadPrescriptionPad(true);
                        organDrugList.setUsingRateId(drugListMatch.getUsingRateId());
                        organDrugList.setUsePathwaysId(drugListMatch.getUsePathwaysId());
                        organDrugList.setSmallestUnitUseDose(drugListMatch.getSmallestUnitUseDose());
                        organDrugList.setRecommendedUseDose(drugListMatch.getRecommendedUseDose());
                        organDrugList.setDrugsEnterpriseIds(drugListMatch.getDrugsEnterpriseIds());
                        if (!ObjectUtils.isEmpty(drugListMatch.getApplyBusiness())) {
                            organDrugList.setApplyBusiness(drugListMatch.getApplyBusiness());
                        } else {
                            organDrugList.setApplyBusiness("1");
                        }
                        organDrugList.setTargetedDrugType(drugListMatch.getTargetedDrugType());
                        organDrugList.setSmallestSaleMultiple(drugListMatch.getSmallestSaleMultiple());

                        Boolean isSuccess = organDrugListDAO.updateData(organDrugList);
                        if (!isSuccess) {
                            OrganDrugList save = organDrugListDAO.save(organDrugList);
                            organDrugSync(save);
                            saveMsg.append("【" + organDrugList.getDrugId() + "-" + organDrugList.getDrugName() + "】");
                            organDrugLists.add(save);
                            num = num + 1;
                        } else {
                            List<OrganDrugList> byDrugIdAndOrganId = organDrugListDAO.findByOrganDrugCodeAndOrganId(organDrugList.getOrganDrugCode(), organDrugList.getOrganId());
                            if (byDrugIdAndOrganId != null && byDrugIdAndOrganId.size() > 0) {
                                for (OrganDrugList drugList : byDrugIdAndOrganId) {
                                    organDrugSync(drugList);
                                }
                            }
                            //更新
                            updateMsg.append("【" + organDrugList.getDrugId() + "-" + organDrugList.getDrugName() + "】");
                        }
                    }
                }
                setResult(organDrugLists);
            }
        };
        HibernateSessionTemplate.instance().executeTrans(action);
        busActionLogService.recordBusinessLogRpcNew("机构药品管理", "", "OrganDrugList", saveMsg.toString(), organDTO.getName());
        busActionLogService.recordBusinessLogRpcNew("机构药品管理", "", "OrganDrugList", updateMsg.toString(), organDTO.getName());
        return action.getResult();
    }

    /**
     * 药品搜索(可根据药品名称，厂家等进行搜索)
     */
    @RpcService
    public QueryResult<DrugListMatch> drugSearch(int organId, String keyWord, Integer status, int start, int limit) {
        return drugListMatchDAO.queryDrugListsByDrugNameAndStartAndLimit(organId, keyWord, status, start, limit);
    }

    /**
     * 药品搜索(可根据药品名称，厂家等进行搜索)
     */
    @RpcService
    public QueryResult<DrugListMatch> drugCheckSearch(Date startTime, Date endTime, int organId, String keyWord, String drugForm, int start, int limit) {
        if (ObjectUtils.isEmpty(startTime)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "startTime is require");
        }
        if (ObjectUtils.isEmpty(endTime)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "endTime is require");
        }
        if (ObjectUtils.isEmpty(organId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is require");
        }
        return drugListMatchDAO.queryDrugListsCheckByDrugNameAndStartAndLimit(startTime, endTime, organId, keyWord, drugForm, start, limit);
    }

    /**
     * 获取用药频率和用药途径
     */
    @RpcService
    public Map<String, Object> getUsingRateAndUsePathway() {
        Map<String, Object> result = Maps.newHashMap();
        List<DictionaryItem> usingRateList = new ArrayList<DictionaryItem>();
        List<DictionaryItem> usePathwayList = new ArrayList<DictionaryItem>();
        try {
            usingRateList = DictionaryController.instance().get("eh.cdr.dictionary.UsingRateWithKey").getSlice(null, 0, "");
            usePathwayList = DictionaryController.instance().get("eh.cdr.dictionary.UsePathwaysWithKey").getSlice(null, 0, "");
        } catch (ControllerException e) {
            LOGGER.error("getUsingRateAndUsePathway() error : ", e);
        }
        result.put("usingRate", usingRateList);
        result.put("usePathway", usePathwayList);
        return result;
    }

    /**
     * 获取用药频率和用药途径
     */
    @RpcService
    public Map<String, Object> findUsingRateAndUsePathwayWithOutKey() {
        Map<String, Object> result = Maps.newHashMap();
        List<DictionaryItem> usingRateList = new ArrayList<DictionaryItem>();
        List<DictionaryItem> usePathwayList = new ArrayList<DictionaryItem>();
        try {
            usingRateList = DictionaryController.instance().get("eh.cdr.dictionary.UsingRate").getSlice(null, 0, "");
            usePathwayList = DictionaryController.instance().get("eh.cdr.dictionary.UsePathways").getSlice(null, 0, "");
        } catch (ControllerException e) {
            LOGGER.error("getUsingRateAndUsePathway() error : ", e);
        }
        result.put("usingRate", usingRateList);
        result.put("usePathway", usePathwayList);
        return result;
    }

    /**
     * 获取用药频率和用药途径--新
     */
    @RpcService
    public Map<String, Object> findUsingRateAndUsePathwayByOrganId(Integer organId) {
        Map<String, Object> result = Maps.newHashMap();
        com.ngari.patient.service.IUsingRateService usingRateService = AppDomainContext.getBean("basic.usingRateService", IUsingRateService.class);
        com.ngari.patient.service.IUsePathwaysService usePathwaysService = AppDomainContext.getBean("basic.usePathwaysService", IUsePathwaysService.class);
        List<UsingRateDTO> usingRates = usingRateService.findAllusingRateByOrganId(organId);
        List<UsePathwaysDTO> usePathways = usePathwaysService.findAllUsePathwaysByOrganId(organId);
        result.put("usingRate", usingRates);
        result.put("usePathway", usePathways);
        return result;
    }

    @RpcService
    public void deleteDrugMatchData(Integer id, Boolean isOrganId) {
        IBusActionLogService busActionLogService = AppDomainContext.getBean("opbase.busActionLogService", IBusActionLogService.class);
        OrganService organService = BasicAPI.getService(OrganService.class);
        if (isOrganId) {
            OrganDTO organDTO = organService.getByOrganId(id);
            drugListMatchDAO.deleteByOrganId(id);
            busActionLogService.recordBusinessLogRpcNew("机构药品管理", "", "drugListMatch", "一键清除【" + organDTO.getName() + "】药品匹配表的数据", organDTO.getName());
        } else {
            drugListMatchDAO.deleteById(id);
        }
    }

    @RpcService
    public void deleteOrganDrugData(Integer id, Boolean isOrganId) {
        if (isOrganId) {
            organDrugListDAO.deleteByOrganId(id);
        } else {
            organDrugListDAO.deleteById(id);
        }
    }

    @RpcService
    public void deleteProvinceDrugData(Integer id, Boolean isProvinceId) {
        if (isProvinceId) {
            if (null == id) {
                LOGGER.warn("当前清除省平台的数据，没有需要的省区域信息！");
            }
            provinceDrugListDAO.deleteByProvinceId(id.toString());
        } else {
            provinceDrugListDAO.deleteByProvinceDrugId(id);
        }
    }


    @RpcService
    public Long getNumBySourceOrgan(Integer organId) {
        if (ObjectUtils.isEmpty(organId)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "organId is required");
        }
        return drugListMatchDAO.getNumBySourceOrgan(organId);
    }


    /**
     * 根据药品id更新匹配表药品机构编码
     *
     * @param map
     */
    @RpcService
    public void updateMatchCodeById(Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            drugListMatchDAO.updateDrugListMatchInfoById(Integer.valueOf(entry.getKey()), ImmutableMap.of("organDrugCode", entry.getValue()));
        }
    }

    /**
     * 根据id更新机构药品表药品机构编码
     *
     * @param map
     */
    @RpcService
    public void updateOrganDrugCodeById(Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            organDrugListDAO.updateOrganDrugById(Integer.valueOf(entry.getKey()), ImmutableMap.of("organDrugCode", entry.getValue()));
        }
    }

    /**
     * @param organId 机构id
     * @param depId   药企id
     * @param flag    是否用机构药品的编码作为药企编码，否就用平台的id作为药企编码
     */
    @RpcService(timeout = 600)
    public void addOrganDrugDataToSaleDrugList(Integer organId, Integer depId, Boolean flag) throws InterruptedException {
        if (organId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "药企关联机构ID参数为null！");
        }
        List<OrganDrugList> drugs = organDrugListDAO.findOrganDrugByOrganId(organId);
        int save = 0;
        int update = 0;
        List<Integer> list1 = Lists.newArrayList();
        List<Integer> list2 = Lists.newArrayList();
        List<List<OrganDrugList>> partition = Lists.partition(drugs, 200);
        final CountDownLatch end = new CountDownLatch(partition.size());
        for (int i = 0; i < partition.size(); i++) {
            int finalI = i;
            RecipeBusiThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    LOGGER.info("addOrganDrugDataToSaleDrugList start");
                    long start = System.currentTimeMillis();
                    try {
                        Map<String, Integer> stringIntegerMap = saveOrUpdateOrganDrugDataToSaleDrugList(partition.get(finalI), organId, depId, flag);
                        list1.add(stringIntegerMap.get("save"));
                        list2.add(stringIntegerMap.get("update"));
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        end.countDown();
                        long elapsedTime = System.currentTimeMillis() - start;
                        LOGGER.info("RecipeBusiThreadPool addOrganDrugDataToSaleDrugList 将机构药品数据保存或者更新到配送目录 执行时间:{}.", elapsedTime);
                    }
                }
            });

        }

        end.await();

        for (int i = 0; i < list1.size(); i++) {
            save = list1.get(i) + save;
        }
        for (int i = 0; i < list2.size(); i++) {
            update = list2.get(i) + update;
        }
        LOGGER.info("addOrganDrugDataToSaleDrugList 新增（save）= " + save + " 个药品 ：修改（update）= " + update + " 个药品!");
        throw new DAOException(DAOException.VALUE_NEEDED, "新增" + save + "个药品，更新" + update + "个药品。");
    }

    @RpcService
    public void updateOrganDrugDataToSaleDrugList(List<OrganDrugList> drugs, Integer depId) {
        SaleDrugList saleDrugList;
        for (OrganDrugList organDrugList : drugs) {
            List<SaleDrugList> byOrganIdAndDrugCode = saleDrugListDAO.findByOrganIdAndDrugCode(depId, organDrugList.getOrganDrugCode());
            if (byOrganIdAndDrugCode != null && byOrganIdAndDrugCode.size() > 0) {
                for (SaleDrugList drugList : byOrganIdAndDrugCode) {
                    drugList.setStatus(organDrugList.getStatus());
                    saleDrugListDAO.update(drugList);
                    LOGGER.info("deleteOrganDrugDataToSaleDrugList 删除（delete）= " + drugList + " 药品 ：机构药品（）= " + organDrugList + " !");
                }
            }
        }
    }

    @RpcService
    public void deleteOrganDrugDataToSaleDrugList(List<OrganDrugList> drugs, Integer depId) {
        SaleDrugList saleDrugList;
        for (OrganDrugList organDrugList : drugs) {
            List<SaleDrugList> byOrganIdAndDrugCode = saleDrugListDAO.findByOrganIdAndDrugCode(depId, organDrugList.getOrganDrugCode());
            SaleDrugList byDrugIdAndOrganId = saleDrugListDAO.getByDrugIdAndOrganId(organDrugList.getDrugId(), depId);
            if (byOrganIdAndDrugCode != null && byOrganIdAndDrugCode.size() > 0) {
                for (SaleDrugList drugList : byOrganIdAndDrugCode) {
                    saleDrugListDAO.remove(drugList.getOrganDrugId());
                    LOGGER.info("deleteOrganDrugDataToSaleDrugList 删除（delete）= " + JSONUtils.toString(drugList) + " 药品 ：机构药品（）= " + JSONUtils.toString(organDrugList) + " !");
                }
            } else if (byDrugIdAndOrganId != null) {
                saleDrugListDAO.remove(byDrugIdAndOrganId.getOrganDrugId());
                LOGGER.info("deleteOrganDrugDataToSaleDrugList 删除2（delete）= " + JSONUtils.toString(byDrugIdAndOrganId) + " 药品 ：机构药品（）= " + JSONUtils.toString(organDrugList) + " !");
            }
        }
    }

    @RpcService
    public Map<String, Integer> saveOrUpdateOrganDrugDataToSaleDrugList(List<OrganDrugList> drugs, Integer organId, Integer depId, Boolean flag) {
        Integer save = 0;
        Integer update = 0;
        SaleDrugList saleDrugList;
        for (OrganDrugList organDrugList : drugs) {
            saleDrugList = new SaleDrugList();
            List<SaleDrugList> byOrganIdAndDrugCode = saleDrugListDAO.findByOrganIdAndDrugCode(depId, organDrugList.getOrganDrugCode());
            SaleDrugList byDrugIdAndOrganId = saleDrugListDAO.getByDrugIdAndOrganId(organDrugList.getDrugId(), depId);
            if (byOrganIdAndDrugCode != null && byOrganIdAndDrugCode.size() > 0) {
                if (organDrugList.getStatus().equals(1)) {
                    SaleDrugList saleDrugList1 = byOrganIdAndDrugCode.get(0);
                    saleDrugList1.setPrice(organDrugList.getSalePrice());
                    saleDrugList1.setDrugId(organDrugList.getDrugId());
                    saleDrugList1.setDrugName(organDrugList.getDrugName());
                    saleDrugList1.setSaleName(organDrugList.getSaleName());
                    saleDrugList1.setDrugSpec(organDrugList.getDrugSpec());
                    saleDrugList1.setStatus(organDrugList.getStatus());
                    saleDrugList1.setLastModify(new Date());
                    saleDrugListDAO.update(saleDrugList1);
                    update++;
                }
            } else if (byDrugIdAndOrganId == null) {
                if (organDrugList.getStatus().equals(1)) {
                    saleDrugList.setDrugId(organDrugList.getDrugId());
                    saleDrugList.setDrugName(organDrugList.getDrugName());
                    saleDrugList.setSaleName(organDrugList.getSaleName());
                    saleDrugList.setDrugSpec(organDrugList.getDrugSpec());
                    saleDrugList.setOrganId(depId);
                    saleDrugList.setStatus(1);
                    saleDrugList.setPrice(organDrugList.getSalePrice());
                    if (flag) {
                        saleDrugList.setOrganDrugCode(organDrugList.getOrganDrugCode());
                    } else {
                        saleDrugList.setOrganDrugCode(String.valueOf(organDrugList.getDrugId()));
                    }
                    saleDrugList.setInventory(new BigDecimal(100));
                    saleDrugList.setCreateDt(new Date());
                    saleDrugList.setLastModify(new Date());
                    saleDrugListDAO.save(saleDrugList);
                    save++;
                } else {
                    continue;
                }
            }

        }
        Map<String, Integer> map = new HashMap<>();
        map.put("save", save);
        map.put("update", update);
        return map;
    }


    /**
     * 上传未匹配数据到通用药品目录
     *
     * @param organId       机构id
     * @param isHaveOrganId 通用药品目录是否包含机构来源
     * @return
     */
    @RpcService
    public Integer uploadNoMatchData(Integer organId, Boolean isHaveOrganId) {
        List<DrugListMatch> data = drugListMatchDAO.findDataByOrganAndStatus(organId, DrugMatchConstant.MARKED);
        if (CollectionUtils.isNotEmpty(data)) {
            for (DrugListMatch drugListMatch : data) {
                DrugList drugList = new DrugList();
                //药品名
                drugList.setDrugName(drugListMatch.getDrugName());
                //商品名
                drugList.setSaleName(drugListMatch.getSaleName());
                //一次剂量
                drugList.setUseDose(drugListMatch.getUseDose());
                //剂量单位
                drugList.setUseDoseUnit(drugListMatch.getUseDoseUnit());
                //规格
                drugList.setDrugSpec(drugListMatch.getDrugSpec());
                //药品包装数量
                drugList.setPack(drugListMatch.getPack());
                //药品单位
                drugList.setUnit(drugListMatch.getUnit());
                //药品类型
                drugList.setDrugType(drugListMatch.getDrugType());
                //剂型
                drugList.setDrugForm(drugListMatch.getDrugForm());
                drugList.setPrice1(drugListMatch.getPrice().doubleValue());
                drugList.setPrice2(drugListMatch.getPrice().doubleValue());
                //厂家
                drugList.setProducer(drugListMatch.getProducer());
                //其他
                drugList.setDrugClass("1901");
                drugList.setAllPyCode("");
                drugList.setStatus(1);
                //来源机构
                if (isHaveOrganId) {
                    drugList.setSourceOrgan(organId);
                }
                DrugList save = drugListDAO.save(drugList);
                if (save != null) {
                    drugListDAO.updateDrugListInfoById(save.getDrugId(), ImmutableMap.of("drugCode", String.valueOf(save.getDrugId())));
                    //更新为已匹配，将已标记上传的药品自动关联上
                    //判断更新成已匹配还是匹配中
                    Integer status;
                    if (isHaveReulationId(organId) && StringUtils.isEmpty(drugListMatch.getRegulationDrugCode())) {
                        //匹配中
                        status = DrugMatchConstant.MATCHING;
                    } else {
                        //已匹配
                        status = DrugMatchConstant.ALREADY_MATCH;
                    }
                    drugListMatchDAO.updateDrugListMatchInfoById(drugListMatch.getDrugId(), ImmutableMap.of("status", status, "matchDrugId", save.getDrugId()));
                }
            }
            return data.size();
        }
        return 0;
    }

    @RpcService
    public boolean isHaveReulationId(Integer organId) {
        String addrArea = checkOrganAddrArea(organId);
        Long provinceDrugNum = provinceDrugListDAO.getCountByProvinceIdAndStatus(addrArea, 1);
        //更新药品状态成匹配中
        if (0L < provinceDrugNum) {
            return true;
        }
        return false;
    }

    /**
     * 批量操作机构 上传机构药品数据到监管平台备案
     *
     * @param organIds
     */
    @RpcService(timeout = 6000)
    public void uploadOrgansDrugToRegulation(List<Integer> organIds) {
        for (Integer organId : organIds) {
            uploadDrugToRegulation(organId);
        }
    }

    /**
     * 上传机构药品数据到监管平台备案
     *
     * @param organId
     */
    @RpcService
    public void uploadDrugToRegulation(Integer organId) {
        List<OrganDrugList> organDrug = organDrugListDAO.findOrganDrugByOrganId(organId);
        for (OrganDrugList organDrugList : organDrug) {
            organDrugListService.uploadDrugToRegulation(organDrugList);
        }

    }

    /**
     * 查询省平台药品列表
     *
     * @param organId
     */
    @RpcService
    public List<ProvinceDrugList> findProvinceDrugList(Integer organId) {
        String addrArea = checkOrganAddrArea(organId);
        return provinceDrugListDAO.findByProvinceIdAndStatus(addrArea, 1);
    }

    /**
     * 根据机构，判断机构关联的省平台药品有没有药品录入
     * 如果已匹配的和已提交设置成匹配中的
     */
    @RpcService
    public void updateOrganDrugMatchByProvinceDrug(int organId) {
        //首先判断his配置中机构有没有监管平台。没有就不返回省平台列表
        if (!checkOrganRegulation(organId)) return;

        String addrArea = checkOrganAddrArea(organId);
        Long provinceDrugNum = provinceDrugListDAO.getCountByProvinceIdAndStatus(addrArea, 1);
        //更新药品状态成匹配中
        if (0L < provinceDrugNum) {
            //批量将匹配药品状态设置成匹配中
            drugListMatchDAO.updateStatusListToStatus(Arrays.asList(Change_Matching_StatusList), organId, DrugMatchConstant.MATCHING);
        }
    }

    @RpcService
    private String checkOrganAddrArea(int organId) {
        //查询是否有意需要更新状态的药品（将匹配药品中已匹配的和已提交）
        //这里直接判断机构对应省药品有无药品
        OrganDTO organDTO = organService.get(organId);
        if (null == organDTO) {
            LOGGER.warn("updateOrganDrugMatchByProvinceDrug 当期机构[{}]不存在", organId);
            return null;
        }
        String addrArea = organDTO.getAddrArea();
        //校验省平台的地址信息合理性
        if (null == addrArea || 2 > addrArea.length()) {
            LOGGER.error("updateOrganDrugMatchByProvinceDrug() error : 医院[{}],对应的省信息不全", organId);
            throw new DAOException(DAOException.VALUE_NEEDED, "医院对应的省信息不全");
        }
        return addrArea.substring(0, 2);
    }

    /**
     * 药品自动匹配
     *
     * @param drugListMatch
     * @return
     */
    @RpcService
    @LogRecord
    public Integer automaticDrugMatch(DrugListMatch drugListMatch, String operator) {
        List<DrugList> drugLists = drugListDAO.findDrugMatchAutomatic(drugListMatch.getDrugName(), drugListMatch.getSaleName(), drugListMatch.getDrugSpec(),
                drugListMatch.getUnit(), drugListMatch.getDrugForm(), drugListMatch.getProducer());
        if (ObjectUtils.isEmpty(drugLists)) {
            if (drugListMatch.getSourceOrgan() != null) {
                drugLists = drugListDAO.findDrugMatchAutomaticOrgan(drugListMatch.getDrugName(), drugListMatch.getSaleName(), drugListMatch.getDrugSpec(),
                        drugListMatch.getUnit(), drugListMatch.getDrugForm(), drugListMatch.getProducer(), drugListMatch.getSourceOrgan());
            }
        }
        Integer status = 0;
        if (drugListMatch.getStatus() != DrugMatchConstant.ALREADY_MATCH && drugListMatch.getStatus() != DrugMatchConstant.SUBMITED) {
            if (drugLists != null && drugLists.size() > 0) {
                UpdateMatchStatusFormBean bean = new UpdateMatchStatusFormBean();
                bean.setDrugId(drugListMatch.getDrugId());
                bean.setMatchDrugId(drugLists.get(0).getDrugId());
                bean.setHaveProvinceDrug(false);
                bean.setOperator(operator);
                bean.setMakeType(0);
                status = updateMatchStatusCurrent(bean);
            } else {
                //如果是已匹配的取消匹配
                //drugListMatchDAO.updateDrugListMatchInfoById(drugListMatch.getDrugId(), ImmutableMap.of("status", DrugMatchConstant.UNMATCH, "operator", operator));
                //updata by maoly on 2020/03/16 自动同步至平台药品库
                DrugList drugList = new DrugList();
                //药品名
                drugList.setDrugName(drugListMatch.getDrugName());
                //商品名
                drugList.setSaleName(drugListMatch.getSaleName());
                //一次剂量
                drugList.setUseDose(drugListMatch.getUseDose());
                //剂量单位
                drugList.setUseDoseUnit(drugListMatch.getUseDoseUnit());
                //规格
                drugList.setDrugSpec(drugListMatch.getDrugSpec());
                //药品包装数量---默认1
                drugList.setPack(drugListMatch.getPack() == null ? 1 : drugListMatch.getPack());
                //药品单位
                drugList.setUnit(drugListMatch.getUnit());
                //药品类型
                drugList.setDrugType(drugListMatch.getDrugType());
                //剂型
                drugList.setDrugForm(drugListMatch.getDrugForm());
                if (!ObjectUtils.isEmpty(drugListMatch.getPrice())) {
                    drugList.setPrice1(drugListMatch.getPrice().doubleValue());
                    drugList.setPrice2(drugListMatch.getPrice().doubleValue());
                }
                //厂家
                drugList.setProducer(drugListMatch.getProducer());
                //药品编码
                drugList.setDrugCode(drugListMatch.getOrganDrugCode());
                //其他
                if (!ObjectUtils.isEmpty(drugList.getDrugType())) {
                    if (drugList.getDrugType() == 1) {
                        drugList.setDrugClass("1901");
                    } else if (drugList.getDrugType() == 2) {
                        drugList.setDrugClass("1801");
                    } else if (drugList.getDrugType() == 3) {
                        drugList.setDrugClass("2001");
                    }
                } else {
                    drugList.setDrugClass("1901");
                }

                drugList.setAllPyCode("");
                drugList.setStatus(1);
                drugList.setIsStandardDrug(0);
                drugList.setCreateDt(new Date());
                drugList.setLastModify(new Date());
                //来源机构
                if (drugListMatch.getSourceOrgan() != null) {
                    DrugSourcesDAO dao = DAOFactory.getDAO(DrugSourcesDAO.class);
                    List<DrugSources> byDrugSourcesId = dao.findByDrugSourcesId(drugListMatch.getSourceOrgan());
                    if (ObjectUtils.isEmpty(byDrugSourcesId)) {
                        OrganService bean = AppDomainContext.getBean("basic.organService", OrganService.class);
                        OrganDTO byOrganId = bean.getByOrganId(drugListMatch.getSourceOrgan());
                        DrugSources saveData = new DrugSources();
                        saveData.setDrugSourcesId(byOrganId.getOrganId());
                        saveData.setDrugSourcesName(byOrganId.getName());
                        DrugSources save = dao.save(saveData);
                        drugList.setSourceOrgan(save.getDrugSourcesId());
                    } else {
                        DrugSources drugSources = byDrugSourcesId.get(0);
                        if (drugSources == null) {
                            drugList.setSourceOrgan(drugListMatch.getSourceOrgan());
                        } else {
                            drugList.setSourceOrgan(drugSources.getDrugSourcesId());
                        }
                    }
                }
                DrugList save = drugListDAO.save(drugList);
                UpdateMatchStatusFormBean bean = new UpdateMatchStatusFormBean();
                bean.setDrugId(drugListMatch.getDrugId());
                bean.setMatchDrugId(save.getDrugId());
                bean.setHaveProvinceDrug(false);
                bean.setOperator(operator);
                bean.setMakeType(0);
                status = updateMatchStatusCurrent(bean);
            }
        }
        return status;
    }

    /**
     * @param updateMatchStatusFormBean 属性值
     *                                  drugId 更新的药品id
     *                                  matchDrugId 匹配上的平台药品id
     *                                  matchDrugInfo 匹配上省平台的药品code
     *                                  operator 操作人
     *                                  haveProvinceDrug 是否有当前机构对应省的药品（由前端查询list是否为空判断）
     * @return void
     * @method updateMatchStatusCurrent
     * @description 选中的平台药品/省平台药品匹配机构药品
     * @date: 2019/10/25
     * @author: JRK
     */
    @RpcService
    public Integer updateMatchStatusCurrent(UpdateMatchStatusFormBean updateMatchStatusFormBean) {
        String operator = updateMatchStatusFormBean.getOperator();
        if (StringUtils.isEmpty(operator)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "operator is required");
        }

        //更新状态数据准备
        Integer drugId = updateMatchStatusFormBean.getDrugId();
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);
        Integer drugListMatchStatus = drugListMatch.getStatus();
        if (null == drugListMatch) {
            LOGGER.warn("updateMatchStatusCurrent 当期对照药品[{}]不存在", drugId);
            return null;
        }
        int makeType = updateMatchStatusFormBean.getMakeType();
        Boolean haveProvinceDrug = updateMatchStatusFormBean.getHaveProvinceDrug();
        Integer matchDrugId = updateMatchStatusFormBean.getMatchDrugId();
        String matchDrugInfo = updateMatchStatusFormBean.getMatchDrugInfo();


        Map<String, Object> updateMap = new HashMap<>();
        //判断是否有省平台药品对应
        Integer status = 0;
        if (Platform_Type == makeType) {
            //平台匹配操作
            if (haveProvinceDrug) {
                //匹配省平台的时候
                status = geUpdateStatus(drugListMatch, Platform_Type);
                if (status == null) return null;
            } else {
                //无匹省台的时候
                status = DrugMatchConstant.ALREADY_MATCH;
            }
            updateMap.put("matchDrugId", matchDrugId);
        } else if (Province_Platform_Type == makeType) {
            //省平台匹配操作
            status = geUpdateStatus(drugListMatch, Province_Platform_Type);
            if (status == null) return null;
            updateMap.put("regulationDrugCode", matchDrugInfo);
        } else {
            LOGGER.info("updateMatchStatusCurrent 传入操作状态非平台和省平台", makeType);
            return null;
        }
        updateMap.put("operator", operator);

        updateMap.put("status", status);
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, updateMap);
        LOGGER.info("updateMatchStatusCurrent 操作人->{}更新已匹配状态,drugId={};status ->before={},after={}", operator, drugId, drugListMatch.getStatus(), status);
        return status;
    }

    /*获取更新后的对照状态状态*/
    private Integer geUpdateStatus(DrugListMatch drugListMatch, int updateType) {
        Integer drugId = drugListMatch.getDrugId();
        Integer drugListMatchStatus = drugListMatch.getStatus();
        boolean haveMatchDrug = null != drugListMatch.getMatchDrugId();
        boolean haveRegulationDrugCode = null != drugListMatch.getRegulationDrugCode();

        Integer status;
        if (Platform_Type == updateType) {
            //平台的更新
            //说明已经匹配，只需要修改匹配的药品代码就行了
            if (haveMatchDrug) {
                //已提交的也可以更新
                if (DrugMatchConstant.SUBMITED == drugListMatchStatus) {
                    if (haveRegulationDrugCode) {
                        status = DrugMatchConstant.ALREADY_MATCH;
                    } else {
                        status = DrugMatchConstant.MATCHING;
                    }
                } else {
                    status = drugListMatchStatus;
                }
            } else {
                //没有匹配的话，判断有没有省药品字段
                if (haveRegulationDrugCode) {
                    status = DrugMatchConstant.ALREADY_MATCH;
                } else {
                    status = DrugMatchConstant.MATCHING;
                }
            }
        } else {
            //省平台的更新
            //说明已经匹配，只需要修改匹配的省药品代码就行了
            if (haveRegulationDrugCode) {
                //已提交的也可以更新
                if (DrugMatchConstant.SUBMITED == drugListMatchStatus) {
                    if (haveMatchDrug) {
                        status = DrugMatchConstant.ALREADY_MATCH;
                    } else {
                        status = DrugMatchConstant.MATCHING;
                    }
                } else {
                    status = drugListMatchStatus;
                }
            } else {
                //没有匹配的话，判断有没有药品字段
                if (haveMatchDrug) {
                    status = DrugMatchConstant.ALREADY_MATCH;
                } else {
                    status = DrugMatchConstant.MATCHING;
                }
            }
        }


        return status;
    }

    /**
     * 省药品详情搜索
     */
    @RpcService
    public ProvinceDrugListBean getprovinceDrugMatch(Integer organId, String provinceDrugcode) {
        if (ObjectUtils.isEmpty(provinceDrugcode)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "未找到机构配置!");
        }
        String addrArea = checkOrganAddrArea(organId);
        ProvinceDrugList provinceDrugList = provinceDrugListDAO.getByProvinceIdAndDrugId(addrArea, provinceDrugcode, 1);
        if (ObjectUtils.isEmpty(provinceDrugList)) {
            return null;
        }
        return ObjectCopyUtils.convert(provinceDrugList, ProvinceDrugListBean.class);

    }

    /**
     * 省药品匹配
     */
    @RpcService
    public List<ProvinceDrugListBean> provinceDrugMatchNew(String name, int organId, int start, int limit, String seacrhString, String producer, String drugType) {
        if (ObjectUtils.isEmpty(name)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "名称为空!");
        }
        List<ProvinceDrugList> provinceDrugLists = getProvinceDrugListsNew(organId, start, limit, seacrhString, producer, drugType, name);
        if (null == provinceDrugLists) {
            //如果没有省平台药品数据则为null
            return null;
        }
        List<ProvinceDrugListBean> provinceDrugListBeans = Lists.newArrayList();
        if (!ObjectUtils.isEmpty(provinceDrugLists)) {
            return ObjectCopyUtils.convert(provinceDrugLists, ProvinceDrugListBean.class);

        }
        return provinceDrugListBeans;

    }

    /*根据匹配的药品销售名，获取相似名称的省平台药品*/
    private List<ProvinceDrugList> getProvinceDrugListsNew(int organId, int start, int limit, String seacrhString, final String producer, String drugType, String name) {
        List<ProvinceDrugList> provinceDrugLists = new ArrayList<>();
        if (!checkOrganRegulation(organId)) return null;

        //判断机构对应省平台下有没有药品，没有省平台返回null
        String addrArea = checkOrganAddrArea(organId);
        Long countByProvinceIdAndStatus = provinceDrugListDAO.getCountByProvinceIdAndStatus(addrArea, 1);
        if (null == countByProvinceIdAndStatus || 0 >= countByProvinceIdAndStatus) {
            return null;
        }
        List<ProvinceDrugList> searchDrugs = Lists.newArrayList();
        //根据药品名取标准药品库查询相关药品
        String likeDrugName = DrugMatchUtil.match(name);
        if (!ObjectUtils.isEmpty(producer)) {
            searchDrugs = provinceDrugListDAO.findByProvinceSaleNameLikeSearch(addrArea, start, limit, seacrhString, producer, drugType);
        } else {
            if (!ObjectUtils.isEmpty(seacrhString)) {
                likeDrugName = seacrhString;
            }
            searchDrugs = provinceDrugListDAO.findByProvinceSaleNameLike(likeDrugName, addrArea, start, limit, seacrhString, drugType);
        }
        if (CollectionUtils.isNotEmpty(searchDrugs)) {
            provinceDrugLists = searchDrugs;
        }

        return provinceDrugLists;
    }


    /* *//*渲染页面上的勾选展示的项*//*
    private List<ProvinceDrugListBean> getProvinceDrugListBeanNew(OrganDrugList organDrugList, List<ProvinceDrugList> provinceDrugLists) {
        List<ProvinceDrugListBean> provinceDrugListBeans = new ArrayList<>();
        //已匹配状态返回匹配药品id
        if (CollectionUtils.isNotEmpty(provinceDrugLists)) {
            provinceDrugListBeans = ObjectCopyUtils.convert(provinceDrugLists, ProvinceDrugListBean.class);
            for (ProvinceDrugListBean provinceDrugListBean : provinceDrugListBeans) {
                //判断当前关联省平台药品code和关联code一致
                if (null != provinceDrugListBean.getProvinceDrugCode() && provinceDrugListBean.getProvinceDrugCode().equals(organDrugList.getRegulationDrugCode())) {
                    provinceDrugListBean.setMatched(true);
                }
            }
        }

        return provinceDrugListBeans;
    }*/


    /**
     * 省药品匹配
     */
    @RpcService
    public List<ProvinceDrugListBean> provinceDrugMatch(int drugId, int organId, int start, int limit, String seacrhString) {
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);

        if (null == drugListMatch) {
            LOGGER.warn("provinceDrugMatch 当期药品[{}]不在机构对照列表中", drugId);
            return null;
        }
        List<ProvinceDrugList> provinceDrugLists = getProvinceDrugLists(organId, drugListMatch, start, limit, seacrhString);
        if (null == provinceDrugLists) {
            //如果没有省平台药品数据则为null
            return null;
        }
        List<ProvinceDrugListBean> provinceDrugListBeans = getProvinceDrugListBean(drugListMatch, provinceDrugLists);

        return provinceDrugListBeans;

    }

    /*根据匹配的药品销售名，获取相似名称的省平台药品*/
    private List<ProvinceDrugList> getProvinceDrugLists(int organId, DrugListMatch drugListMatch, int start, int limit, String seacrhString) {
        List<ProvinceDrugList> provinceDrugLists = new ArrayList<>();
        if (!checkOrganRegulation(organId)) return null;

        //判断机构对应省平台下有没有药品，没有省平台返回null
        String addrArea = checkOrganAddrArea(organId);
        Long countByProvinceIdAndStatus = provinceDrugListDAO.getCountByProvinceIdAndStatus(addrArea, 1);
        if (null == countByProvinceIdAndStatus || 0 >= countByProvinceIdAndStatus) {
            return null;
        }

        //根据药品名取标准药品库查询相关药品
        String likeDrugName = DrugMatchUtil.match(drugListMatch.getDrugName());

        List<ProvinceDrugList> searchDrugs = provinceDrugListDAO.findByProvinceSaleNameLike(likeDrugName, addrArea, start, limit, seacrhString, null);
        if (CollectionUtils.isNotEmpty(searchDrugs)) {
            provinceDrugLists = searchDrugs;
        }

        return provinceDrugLists;
    }

    /**
     * 省药品匹配 搜索专用
     */
    @RpcService
    public List<ProvinceDrugListBean> provinceDrugMatchSearch(int drugId, int organId, int start, int limit, final String input, final String producer) {
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);

        if (null == drugListMatch) {
            LOGGER.warn("provinceDrugMatch 当期药品[{}]不在机构对照列表中", drugId);
            return null;
        }
       /* List<ProvinceDrugList> provinceDrugLists =null;
        if (input != null){
            provinceDrugLists = getProvinceDrugLists(organId,drugListMatch, start, limit, input);
        }else {
            provinceDrugLists = getProvinceDrugLists(organId,drugListMatch, start, limit, producer);
        }*/
        List<ProvinceDrugList> provinceDrugLists = getProvinceDrugListsSearch(organId, start, limit, input, producer);
        if (null == provinceDrugLists) {
            //如果没有省平台药品数据则为null
            return null;
        }
        List<ProvinceDrugListBean> provinceDrugListBeans = getProvinceDrugListBean(drugListMatch, provinceDrugLists);

        return provinceDrugListBeans;

    }


    /*根据匹配的药品销售名，获取相似名称的省平台药品   搜索专用*/
    private List<ProvinceDrugList> getProvinceDrugListsSearch(int organId, int start, int limit, String input, String producer) {
        List<ProvinceDrugList> provinceDrugLists = new ArrayList<>();
        if (!checkOrganRegulation(organId)) return null;

        //判断机构对应省平台下有没有药品，没有省平台返回null
        String addrArea = checkOrganAddrArea(organId);
        Long countByProvinceIdAndStatus = provinceDrugListDAO.getCountByProvinceIdAndStatus(addrArea, 1);
        if (null == countByProvinceIdAndStatus || 0 >= countByProvinceIdAndStatus) {
            return null;
        }

        List<ProvinceDrugList> searchDrugs = provinceDrugListDAO.findByProvinceSaleNameLikeSearch(addrArea, start, limit, input, producer, null);
        if (CollectionUtils.isNotEmpty(searchDrugs)) {
            provinceDrugLists = searchDrugs;
        }

        return provinceDrugLists;
    }

    /*判断当前机构下的建挂壁平台有没有配置，没有配置判断为没有省平台药品*/
    @RpcService
    public boolean checkOrganRegulation(int organId) {
        IRegulationService regulationService = AppDomainContext.getBean("his.regulationService", IRegulationService.class);
        try {
            Boolean haveList = regulationService.checkRegulationList();
            if (!haveList) {
                //没有这个配置，则说明是互联网，默认关联互联网平台的，不需要在查有没有关联互联网平台
                return true;
            }
        } catch (Exception e) {
            LOGGER.error("查询互联网列表失败.", e);
        }

        //首先判断his配置中机构有没有监管平台。没有就不返回省平台列表
        IHisServiceConfigService configService = AppDomainContext.getBean("his.hisServiceConfig", IHisServiceConfigService.class);
        HisResponseTO<ServiceConfigResponseTO> configResponse = configService.queryHisServiceConfigByOrganid(organId);
        if (null != configResponse) {
            ServiceConfigResponseTO config = configResponse.getData();
            if (null == config || null == config.getRegulation()) {
                LOGGER.info("当前机构[{}]没有配置监管平台", organId);
                return false;
            }
        } else {
            LOGGER.warn("没有对应当前机构[{}]的配置", organId);
            return false;
        }
        return true;
    }

    /*渲染页面上的勾选展示的项*/
    private List<ProvinceDrugListBean> getProvinceDrugListBean(DrugListMatch drugListMatch, List<ProvinceDrugList> provinceDrugLists) {
        List<ProvinceDrugListBean> provinceDrugListBeans = new ArrayList<>();
        //已匹配状态返回匹配药品id
        if (CollectionUtils.isNotEmpty(provinceDrugLists)) {
            provinceDrugListBeans = ObjectCopyUtils.convert(provinceDrugLists, ProvinceDrugListBean.class);
            if (drugListMatch.getStatus().equals(DrugMatchConstant.ALREADY_MATCH) || drugListMatch.getStatus().equals(DrugMatchConstant.SUBMITED) || drugListMatch.getStatus().equals(DrugMatchConstant.MATCHING)) {
                for (ProvinceDrugListBean provinceDrugListBean : provinceDrugListBeans) {
                    //判断当前关联省平台药品code和关联code一致
                    if (null != provinceDrugListBean.getProvinceDrugCode() && provinceDrugListBean.getProvinceDrugCode().equals(drugListMatch.getRegulationDrugCode())) {
                        provinceDrugListBean.setMatched(true);
                    }
                }
            }
        }

        return provinceDrugListBeans;
    }

    /**
     * 取消已匹配状态和已提交状态
     */
    @RpcService
    public Integer cancelMatchStatusByOrgan(Integer drugId, String operator, Integer makeType, Boolean haveProvinceDrug) {
        if (StringUtils.isEmpty(operator)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "operator is required");
        }
        Map<String, Object> updateMap = new HashMap<>();
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);
        Integer status = 0;
        if (Platform_Type == makeType) {
            if (haveProvinceDrug) {
                status = getCancelStatus(drugListMatch, "cancelMatchStatusByOrgan 当前匹配药品[{}]状态[{}]不能取消平台匹配");
                if (status == null) return status;
            } else {
                status = DrugMatchConstant.UNMATCH;
            }
            updateMap.put("matchDrugId", null);
        } else if (Province_Platform_Type == makeType) {
            status = getCancelStatus(drugListMatch, "cancelMatchStatusByOrgan 当前匹配药品[{}]状态[{}]不能取消省平台匹配");
            if (status == null) return status;
            updateMap.put("regulationDrugCode", null);
        } else {
            LOGGER.info("cancelMatchStatusByOrgan 传入操作状态非平台和省平台", makeType);
            return status;
        }
        updateMap.put("status", status);
        updateMap.put("operator", operator);
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, updateMap);
        LOGGER.info("cancelMatchStatusByOrgan 操作人取消关联->{}更新状态,drugId={};status ->before={},after={}", operator, drugId, drugListMatch.getStatus(), status);
        return status;
    }

    /*获取取消匹配后的对照状态状态*/
    private Integer getCancelStatus(DrugListMatch drugListMatch, String message) {
        Integer drugId = drugListMatch.getDrugId();
        Integer drugListMatchStatus = drugListMatch.getStatus();
        boolean haveMatchDrug = null != drugListMatch.getMatchDrugId();
        boolean haveRegulationDrugCode = null != drugListMatch.getRegulationDrugCode();

        Integer status;
        if (DrugMatchConstant.ALREADY_MATCH == drugListMatchStatus) {
            status = DrugMatchConstant.MATCHING;
        } else if (DrugMatchConstant.MATCHING == drugListMatchStatus) {
            status = DrugMatchConstant.UNMATCH;
        } else if (DrugMatchConstant.SUBMITED == drugListMatchStatus) {
            if ((!haveMatchDrug && haveRegulationDrugCode) && (haveMatchDrug && !haveRegulationDrugCode)) {
                status = DrugMatchConstant.UNMATCH;
            } else if (haveMatchDrug && haveRegulationDrugCode) {
                status = DrugMatchConstant.MATCHING;
            } else {
                LOGGER.info(message, drugId, drugListMatchStatus);
                return null;
            }
        } else {
            LOGGER.info(message, drugId, drugListMatchStatus);
            return null;
        }
        return status;
    }

    @RpcService(timeout = 60)
    public void drugImportAutoMatch(Integer organId) {
        LOGGER.info("THE DrugMatchOrganId=[{}]", organId);
        GlobalEventExecFactory.instance().getExecutor().submit(new Runnable() {
            @Override
            public void run() {
                Boolean flag = checkOrganRegulation(organId);
                String addrArea = checkOrganAddrArea(organId);
                List<DrugListMatch> result = drugListMatchDAO.findDataByOrganAndStatus(organId, DrugMatchConstant.UNMATCH);
                for (DrugListMatch drug : result) {
                    DrugList drugList = drugListDAO.get(drug.getPlatformDrugId());
                    if (flag) {
                        ProvinceDrugList provinceDrugList = provinceDrugListDAO.getByProvinceIdAndDrugId(addrArea, drug.getRegulationDrugCode(), 1);
                        if (drugList != null && provinceDrugList != null) {
                            // 以匹配
                            drug.setStatus(DrugMatchConstant.ALREADY_MATCH);
                            drug.setMatchDrugId(drugList.getDrugId());
                        } else if (drugList == null && provinceDrugList == null) {
                            //未匹配
                            drug.setStatus(DrugMatchConstant.UNMATCH);
                            drug.setRegulationDrugCode(null);
                            drug.setPlatformDrugId(null);
                        } else if (drugList != null && provinceDrugList == null) {
                            //匹配中
                            drug.setStatus(DrugMatchConstant.MATCHING);
                            drug.setRegulationDrugCode(null);
                        } else if (drugList == null && provinceDrugList != null) {
                            // 匹配中
                            drug.setStatus(DrugMatchConstant.MATCHING);
                            drug.setPlatformDrugId(null);
                        }
                    } else {
                        drug.setRegulationDrugCode(null);
                        if (drugList != null) {
                            drug.setStatus(DrugMatchConstant.ALREADY_MATCH);
                            drug.setMatchDrugId(drugList.getDrugId());
                        } else {
                            drug.setPlatformDrugId(null);
                        }
                    }
                    drugListMatchDAO.updateData(drug);
                }
            }
        });
    }

    /**
     * 判断该机构是否关联省平台（包括互联网医院）供前端调用
     *
     * @param organId
     * @return
     */
    @RpcService
    public Boolean judgeShowProvinceCode(Integer organId) {
        if (checkOrganRegulation(organId) && isHaveReulationId(organId)) {
            return true;
        }
        return false;
    }

    /**
     * 判断该机构是否关联省平台（包括互联网医院）供前端调用
     *
     * @param organId
     * @return
     */
    @RpcService
    public List<ImportDrugRecord> findImportDrugRecordByOrganId(Integer organId) {
        return importDrugRecordDAO.findImportDrugRecordByOrganId(organId);
    }


    /**
     * 获取医生用药频次、途径使用次数统计
     *
     * @param organId
     * @param doctorId
     * @return
     */
    @RpcService
    @LogRecord
    public Map<String, Object> findDrugUsageCountForDoctor(Integer organId, Integer doctorId) {
        if (null == organId || null == doctorId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "机构id医生id不能为空");
        }
        Map<String, Object> result = Maps.newHashMap();
        com.ngari.patient.service.IUsingRateService usingRateService = AppDomainContext.getBean("basic.usingRateService", IUsingRateService.class);
        com.ngari.patient.service.IUsePathwaysService usePathwaysService = AppDomainContext.getBean("basic.usePathwaysService", IUsePathwaysService.class);
        List<UsingRateDTO> usingRates = usingRateService.findAllusingRateByOrganId(organId);
        List<UsePathwaysDTO> usePathways = usePathwaysService.findAllUsePathwaysByOrganId(organId);
        result.put("usingRate", usingRates);
        result.put("usePathway", usePathways);

        // 处理医生使用次数排序逻辑
        handleRateAndPathwayUsage(organId, doctorId, result, usingRates, usePathways);

        return result;
    }

    /**
     * 获取医生用药频次、途径使用次数统计
     *
     * @param organId
     * @param doctorId
     * @param recipeType 当处方类型为空时表示查询所有处方类型  1:西药方 2:成药方 3:草药方 4:膏方
     * @return
     */
    @RpcService
    @LogRecord
    public Map<String, Object> findDrugUsageCountForDoctorNew(Integer organId, Integer doctorId, Integer recipeType) {
        if (null == organId || null == doctorId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "机构id医生id不能为空");
        }
        Map<String, Object> result = Maps.newHashMap();
        com.ngari.patient.service.IUsingRateService usingRateService = AppDomainContext.getBean("basic.usingRateService", IUsingRateService.class);
        com.ngari.patient.service.IUsePathwaysService usePathwaysService = AppDomainContext.getBean("basic.usePathwaysService", IUsePathwaysService.class);
        List<UsingRateDTO> usingRates = usingRateService.findAllusingRateByOrganId(organId);
        List<UsePathwaysDTO> usePathways = usePathwaysService.findAllUsePathwaysByOrganId(organId);
        LOGGER.info("findDrugUsageCountForDoctor all usingRates:{}", JSONUtils.toString(usingRates));
        LOGGER.info("findDrugUsageCountForDoctor all usePathways:{}", JSONUtils.toString(usePathways));
        if (recipeType != null) {
            usingRates = usingRates.stream().filter(u -> u.getCategory() != null && u.getCategory().contains(recipeType.toString())).collect(Collectors.toList());
            usePathways = usePathways.stream().filter(u -> u.getCategory() != null && u.getCategory().contains(recipeType.toString())).collect(Collectors.toList());
        }
        result.put("usingRate", usingRates);
        result.put("usePathway", usePathways);
        // 处理医生使用次数排序逻辑
        handleRateAndPathwayUsage(organId, doctorId, result, usingRates, usePathways);
        LOGGER.info("findDrugUsageCountForDoctor res:{}", JSONUtils.toString(result));
        return result;
    }


    @RpcService
    public DrugEnterpriseResult testAldyDrug() {
        DrugEnterpriseResult result = DrugEnterpriseResult.getSuccess();
        // 校验参数组装
        List<Map<String, Object>> paramList = new ArrayList<>();
        Map<String, Object> drugMap = new HashedMap();
        drugMap.put("drugCode", "1519309");
        drugMap.put("total", "10");
        drugMap.put("unit", "片");
        paramList.add(drugMap);

        String signKey = "hydee";
        String compid = "1402";
        String signStr = "compid=" + compid + "&" + "drugList=" + JSONObject.toJSONString(paramList) + "&" + "key=" + signKey;
        String signResult = Md5Utils.crypt(signStr);
        Map<String, Object> paramMap = new HashedMap();
        paramMap.put("compid", compid);
        paramMap.put("drugList", paramList);
        paramMap.put("sign", signResult);
        // 校验请求
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            String requestUrl = "http://10.153.184.78:58081/ht/zxcf_store";
            HttpPost httpPost = new HttpPost(requestUrl);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(JSONObject.toJSONString(paramMap), ContentType.APPLICATION_JSON));
            LOGGER.info("AldyfRemoteService.scanStock req={}", JSONObject.toJSONString(paramMap));
            HttpResponse httpResponse = httpClient.execute(httpPost);
            LOGGER.info("AldyfRemoteService.scanStock res={}", JSONObject.toJSONString(httpResponse));
            HttpEntity entity = httpResponse.getEntity();
            String response = EntityUtils.toString(entity);
            JSONObject responseObject = JSON.parseObject(response);
            String code = responseObject.getString("code");
            if ("1".equals(code)) {
                JSONArray drugArray = responseObject.getJSONArray("drugList");
                if (null != drugArray && drugArray.size() > 0) {
                    for (int i = 0; i < drugArray.size(); i++) {
                        JSONObject drug = drugArray.getJSONObject(i);
                        if ("false".equals(drug.getString("inventory"))) {
                            result.setMsg("库存校验不通过");
                            result.setCode(DrugEnterpriseResult.FAIL);
                        }
                    }
                }
            } else {
                result.setMsg("库存校验返回失败");
                result.setCode(DrugEnterpriseResult.FAIL);
            }
        } catch (Exception e) {
            LOGGER.error("AldyfRemoteService.scanStock 库存校验异常：", e);
            result.setMsg("库存校验异常");
            result.setCode(DrugEnterpriseResult.FAIL);
        } finally {
            try {
                httpClient.close();
            } catch (Exception e) {
                LOGGER.warn("资源关闭失败：", e);
            }
        }
        return result;
    }

    @RpcService
    public void checkRecipeDrugList(List<DoctorDrugUsageRequest> request) {
        request.forEach(this::checkRecipeDrug);
    }

    /**
     * 处方校验-目前用于增加用于频次、途径使用次数
     *
     * @param request
     */
    @RpcService
    public void checkRecipeDrug(DoctorDrugUsageRequest request) {
        DoctorDrugUsageCountDAO usageCountDAO = DAOFactory.getDAO(DoctorDrugUsageCountDAO.class);
        Integer organId = request.getOrganId();
        Integer doctorId = request.getDoctorId();
        // 处理用药频次使用次数
        if (null != request.getUsingRateId()) {
            DoctorDrugUsageCount rateCount = usageCountDAO.getDoctorUsage(organId, doctorId, RecipeSystemConstant.USAGE_TYPE_RATE, request.getUsingRateId());
            if (null == rateCount) {
                DoctorDrugUsageCount usageCount = new DoctorDrugUsageCount();
                usageCount.setOrganId(organId);
                usageCount.setDoctorId(doctorId);
                usageCount.setDeptId(request.getDeptId());
                usageCount.setUsageType(RecipeSystemConstant.USAGE_TYPE_RATE);
                usageCount.setUsageId(request.getUsingRateId());
                usageCount.setUsageCount(request.getAddCount());
                usageCount.setCreateTime(new Date());
                usageCount.setUpdateTime(new Date());
                usageCountDAO.save(usageCount);
            } else {
                Integer usageCount = rateCount.getUsageCount() + request.getAddCount();
                usageCountDAO.updateUsageCountById(rateCount.getId(), usageCount);
            }
        }
        //处理用药途径使用次数
        if (null != request.getUsePathwayId()) {
            DoctorDrugUsageCount pathwayCount = usageCountDAO.getDoctorUsage(organId, doctorId, RecipeSystemConstant.USAGE_TYPE_PATHWAY, request.getUsePathwayId());
            if (null == pathwayCount) {
                DoctorDrugUsageCount usageCount = new DoctorDrugUsageCount();
                usageCount.setOrganId(organId);
                usageCount.setDoctorId(doctorId);
                usageCount.setDeptId(request.getDeptId());
                usageCount.setUsageType(RecipeSystemConstant.USAGE_TYPE_PATHWAY);
                usageCount.setUsageId(request.getUsePathwayId());
                usageCount.setUsageCount(request.getAddCount());
                usageCount.setCreateTime(new Date());
                usageCount.setUpdateTime(new Date());
                usageCountDAO.save(usageCount);
            } else {
                Integer usageCount = pathwayCount.getUsageCount() + request.getAddCount();
                usageCountDAO.updateUsageCountById(pathwayCount.getId(), usageCount);
            }
        }
    }

    /**
     * 处理医生用药频次和途径使用次数排序逻辑
     *
     * @param organId
     * @param doctorId
     * @param result
     * @param usingRates
     * @param usePathways
     */
    private void handleRateAndPathwayUsage(Integer organId, Integer doctorId, Map<String, Object> result, List<UsingRateDTO> usingRates, List<UsePathwaysDTO> usePathways) {
        DoctorDrugUsageCountDAO usageCountDAO = DAOFactory.getDAO(DoctorDrugUsageCountDAO.class);
        // 用药频次使用记录
        List<DoctorDrugUsageCount> rateCounts = usageCountDAO.findByUsageTypeForDoctor(organId, doctorId, RecipeSystemConstant.USAGE_TYPE_RATE);
        if (CollectionUtils.isNotEmpty(usingRates) && CollectionUtils.isNotEmpty(rateCounts)) {
            List<UsingRateDTO> rateUseCount = new ArrayList<>();
            for (DoctorDrugUsageCount count : rateCounts) {
                for (UsingRateDTO rate : usingRates) {
                    if (rate.getId().equals(count.getUsageId())) {
                        rate.setUsageCount(count.getUsageCount());
                        rateUseCount.add(rate);
                    }
                }
            }

            usingRates.removeAll(rateUseCount);
            rateUseCount.addAll(usingRates);
            result.put("usingRate", rateUseCount);
        }
        // 用药途径使用记录
        List<DoctorDrugUsageCount> pathwayCounts = usageCountDAO.findByUsageTypeForDoctor(organId, doctorId, RecipeSystemConstant.USAGE_TYPE_PATHWAY);
        if (CollectionUtils.isNotEmpty(usePathways) && CollectionUtils.isNotEmpty(pathwayCounts)) {
            List<UsePathwaysDTO> pathwayUseCount = new ArrayList<>();
            for (DoctorDrugUsageCount count : pathwayCounts) {
                for (UsePathwaysDTO pathway : usePathways) {
                    if (pathway.getId().equals(count.getUsageId())) {
                        pathway.setUsageCount(count.getUsageCount());
                        pathwayUseCount.add(pathway);
                    }
                }
            }
            usePathways.removeAll(pathwayUseCount);
            pathwayUseCount.addAll(usePathways);
            result.put("usePathway", pathwayUseCount);
        }
    }

    @RpcService
    public Boolean judgePlatformDrugDelete(int drugId) {
        Long organNum = RecipeAPI.getService(IOrganDrugListService.class).getCountByDrugId(drugId);
        Long saleNum = RecipeAPI.getService(ISaleDrugListService.class).getCountByDrugId(drugId);
        if (organNum > 0 || saleNum > 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 处理处方历史物流数据同步至基础服务
     */
    @RpcService
    public List<String> handleRecipeLogistics() {
        RecipeOrderDAO orderDAO = DAOFactory.getDAO(RecipeOrderDAO.class);
        List<RecipeOrder> orders = orderDAO.findRecipeOrderWitchLogistics();
        List<String> errorList = new ArrayList<>();
        for (RecipeOrder order : orders) {
            try {
                WriteBackLogisticsOrderDto logisticsOrder = new WriteBackLogisticsOrderDto();
                // 机构id
                logisticsOrder.setOrganId(order.getOrganId());
                // 平台用户id
                logisticsOrder.setUserId(order.getMpiId());
                // 业务类型
                logisticsOrder.setBusinessType(DrugEnterpriseConstant.BUSINESS_TYPE);
                // 业务编码
                logisticsOrder.setBusinessNo(order.getOrderCode());
                // 快递编码
                logisticsOrder.setLogisticsCode(order.getLogisticsCompany() + "");
                // 运单号
                logisticsOrder.setWaybillNo(order.getTrackingNumber());
                // 运单费
                logisticsOrder.setWaybillFee(order.getExpressFee());
                // 收件人名称
                logisticsOrder.setAddresseeName(order.getReceiver());
                // 收件人手机号
                logisticsOrder.setAddresseePhone(order.getRecMobile());
                // 收件省份
                logisticsOrder.setAddresseeProvince(LocalStringUtil.getAddressDic(order.getAddress1()));
                // 收件城市
                logisticsOrder.setAddresseeCity(LocalStringUtil.getAddressDic(order.getAddress2()));
                // 收件镇/区
                logisticsOrder.setAddresseeDistrict(LocalStringUtil.getAddressDic(order.getAddress3()));
                // 收件人街道
                logisticsOrder.setAddresseeStreet(LocalStringUtil.getAddressDic(order.getStreetAddress()));
                // 收件详细地址
                logisticsOrder.setAddresseeAddress(order.getAddress4());
                // 寄托物名称
                logisticsOrder.setDepositumName(DrugEnterpriseConstant.DEPOSITUM_NAME);
                PatientService patientService = BasicAPI.getService(PatientService.class);
                PatientDTO patientDTO = patientService.getPatientByMpiId(order.getMpiId());
                if (patientDTO != null) {
                    logisticsOrder.setPatientName(patientDTO.getPatientName());
                    logisticsOrder.setPatientPhone(patientDTO.getMobile());
                    logisticsOrder.setPatientIdentityCardNo(patientDTO.getIdcard());
                }
                LOGGER.info("处方物流历史数据同步，入参={}", JSONObject.toJSONString(logisticsOrder));
                ILogisticsOrderService logisticsOrderService = AppContextHolder.getBean("infra.logisticsOrderService", ILogisticsOrderService.class);
                String writeResult = logisticsOrderService.writeBackLogisticsOrder(logisticsOrder);
                LOGGER.info("处方物流历史数据同步，结果={}", writeResult);
            } catch (Exception e) {
                errorList.add(order.getOrderCode());
                LOGGER.error("处方物流历史数据同步,异常=", e);
            }
        }
        return errorList;
    }

    /**
     * 同步自健药企药品
     *
     * @param detail
     */
    public void organDrugSync(OrganDrugList detail) {
        DrugsEnterpriseDAO dao = DAOFactory.getDAO(DrugsEnterpriseDAO.class);
        List<DrugsEnterprise> drugsEnterprises = dao.findByOrganId(detail.getOrganId());
        DrugsEnterpriseConfigService bean = AppContextHolder.getBean("eh.drugsEnterpriseConfigService", DrugsEnterpriseConfigService.class);
        if (drugsEnterprises != null && drugsEnterprises.size() > 0) {
            for (DrugsEnterprise drugsEnterpris : drugsEnterprises) {
                DrugsEnterpriseConfig config = bean.getConfigByDrugsenterpriseId(drugsEnterpris.getId());
                try {
                    if (config.getEnable_drug_sync() == 1) {
                        if (config.getSyncDataRange() == 1) {
                            //同步数据范围 配送药企
                            if (!ObjectUtils.isEmpty(detail.getDrugsEnterpriseIds())) {
                                String[] split = detail.getDrugsEnterpriseIds().split(",");
                                List<String> userIdList = new ArrayList<String>(Arrays.asList(split));
                                if (userIdList.indexOf(drugsEnterpris.getId().toString()) != -1) {
                                    Map<String, Integer> stringIntegerMap = saleDrugToolService.syncOrganDrugDataToSaleDrugList(detail, config, drugsEnterpris.getId());
                                }
                            }
                        } else if (config.getSyncDataRange() == 2) {
                            //同步数据范围 药品类型
                            if (ObjectUtils.isEmpty(config.getSyncDrugType())) {
                                throw new DAOException(DAOException.VALUE_NEEDED, "未找到该药企[同步药品类型]配置数据!");
                            }
                            if (ObjectUtils.isEmpty(config.getSyncDrugType())) {
                                throw new DAOException(DAOException.VALUE_NEEDED, "未找到该药企[数据同步类型]配置数据!");
                            }
                            String[] strings1 = config.getEnable_drug_syncType().split(",");
                            List<String> syncDrugTypeList = new ArrayList<String>(Arrays.asList(strings1));
                            //西药
                            if (syncDrugTypeList.indexOf("1") != -1) {
                                if (drugListDAO.get(detail.getDrugId()).getDrugType() == 1) {
                                    Map<String, Integer> stringIntegerMap = saleDrugToolService.syncOrganDrugDataToSaleDrugList(detail, config, drugsEnterpris.getId());
                                }
                            }
                            //中成药
                            if (syncDrugTypeList.indexOf("2") != -1) {
                                if (drugListDAO.get(detail.getDrugId()).getDrugType() == 2) {
                                    Map<String, Integer> stringIntegerMap = saleDrugToolService.syncOrganDrugDataToSaleDrugList(detail, config, drugsEnterpris.getId());
                                }
                            }
                            //中药
                            if (syncDrugTypeList.indexOf("3") != -1) {
                                if (drugListDAO.get(detail.getDrugId()).getDrugType() == 3) {
                                    Map<String, Integer> stringIntegerMap = saleDrugToolService.syncOrganDrugDataToSaleDrugList(detail, config, drugsEnterpris.getId());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.info("批量新增机构药品新增修改同步对应药企" + e);

                }
            }
        }
    }

    /**
     * 中间表药品搜索
     *
     * @param drugListMatchId
     */
    @RpcService
    public DrugListMatch getDrugListMatch(Integer drugListMatchId) {
        return drugListMatchDAO.get(drugListMatchId);
    }


    @RpcService
    public DrugSources saveDrugSources(Integer drugSourcesId, String drugSourcesName) {
        DrugSourcesDAO dao = DAOFactory.getDAO(DrugSourcesDAO.class);
        DrugSources saveData = new DrugSources();
        saveData.setDrugSourcesId(drugSourcesId);
        saveData.setDrugSourcesName(drugSourcesName);
        return dao.save(saveData);
    }

}
