package recipe.drugTool.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ngari.patient.dto.OrganDTO;
import com.ngari.patient.service.OrganService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.RecipeAPI;
import com.ngari.recipe.drug.model.DrugListBean;
import com.ngari.recipe.drug.model.ProvinceDrugListBean;
import com.ngari.recipe.drugTool.service.IDrugToolService;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.recipe.model.UpdateMatchStatusFormBean;
import ctd.controller.exception.ControllerException;
import ctd.dictionary.DictionaryController;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.persistence.support.hibernate.template.AbstractHibernateStatelessResultAction;
import ctd.persistence.support.hibernate.template.HibernateSessionTemplate;
import ctd.persistence.support.hibernate.template.HibernateStatelessResultAction;
import ctd.util.BeanUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hibernate.StatelessSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.bean.OrganToolBean;
import recipe.constant.DrugMatchConstant;
import recipe.dao.*;
import recipe.service.OrganDrugListService;
import recipe.util.DrugMatchUtil;
import recipe.util.RedisClient;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * created by shiyuping on 2019/2/1
 */
@RpcBean(value = "drugToolService", mvc_authentication = false)
public class DrugToolService implements IDrugToolService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DrugToolService.class);

    private double progress;

    private RedisClient redisClient = RedisClient.instance();

    private static final String SUFFIX_2003 = ".xls";
    private static final String SUFFIX_2007 = ".xlsx";
    //全局map
    private ConcurrentHashMap<String, Double> progressMap = new ConcurrentHashMap<>();
    /**
     * 用于药品小工具搜索历史记录缓存
     */
    private ConcurrentHashMap<String, ArrayBlockingQueue> cmap = new ConcurrentHashMap<>();

    /**
     * 修改匹配中的原状态（已提交，已匹配）
     */
    public static final Integer[] Change_Matching_StatusList = {DrugMatchConstant.ALREADY_MATCH, DrugMatchConstant.SUBMITED};

    /**
     * 修改匹配中的原状态（已提交，已匹配）
     */
    public static final Integer[] Ready_Match_StatusList = {DrugMatchConstant.ALREADY_MATCH, DrugMatchConstant.SUBMITED};

    /*平台类型*/
    private static final int Platform_Type = 0;

    /*省平台类型*/
    private static final int Province_Platform_Type = 1;

    @Resource
    private DrugListMatchDAO drugListMatchDAO;

    @Resource
    private DrugListDAO drugListDAO;

    @Resource
    private OrganDrugListDAO organDrugListDAO;

    @Resource
    private DrugToolUserDAO drugToolUserDAO;

    @Resource
    private OrganService organService;
    @Resource
    private OrganDrugListService organDrugListService;

    @Resource
    private ProvinceDrugListDAO provinceDrugListDAO;

    private LoadingCache<String, List<DrugList>> drugListCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build(new CacheLoader<String, List<DrugList>>() {
        @Override
        public List<DrugList> load(String str) throws Exception {
            return drugListDAO.findBySaleNameLike(str);
        }
    });

    private LoadingCache<String, List<ProvinceDrugList>> provinceDrugListCache = CacheBuilder.newBuilder().expireAfterWrite(10, TimeUnit.MINUTES).build(new CacheLoader<String, List<ProvinceDrugList>>() {
        @Override
        public List<ProvinceDrugList> load(String searchStr) throws Exception {
            String[] searchStrs = searchStr.split("-");
            return provinceDrugListDAO.findByProvinceSaleNameLike(searchStrs[1], searchStrs[0]);
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
    public synchronized Map<String, Object> readDrugExcel(byte[] buf, String originalFilename, int organId, String operator) {
        LOGGER.info(operator + "开始 readDrugExcel 方法" + System.currentTimeMillis() + "当前进程=" + Thread.currentThread().getName());
        progress = 0;
        String key = organId + operator;
        if (redisClient.exists(key)) {
            redisClient.del(key);
        }
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
        InputStream is = new ByteArrayInputStream(buf);
        //获得用户上传工作簿
        Workbook workbook = null;
        try {
            if (originalFilename.endsWith(SUFFIX_2003)) {
                workbook = new HSSFWorkbook(is);
            } else if (originalFilename.endsWith(SUFFIX_2007)) {
                //使用InputStream需要将所有内容缓冲到内存中，这会占用空间并占用时间
                //当数据量过大时，这里会非常耗时
                workbook = new XSSFWorkbook(is);
            } else {
                result.put("code", 609);
                result.put("msg", "上传文件格式有问题");
                return result;
            }
        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("readDrugExcel error ," + e.getMessage());
            result.put("code", 609);
            result.put("msg", "上传文件格式有问题");
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
        for (int rowIndex = 0; rowIndex <= total; rowIndex++) {
            //循环获得每个行
            row = sheet.getRow(rowIndex);
            //判断是否是模板
            if (rowIndex == 0) {
                String drugCode = getStrFromCell(row.getCell(0));
                String drugName = getStrFromCell(row.getCell(1));
                String retrievalCode = getStrFromCell(row.getCell(18));
                if ("药品编号".equals(drugCode.trim()) && "药品通用名".equals(drugName.trim()) && "院内检索码".equals(retrievalCode.trim())) {
                    continue;
                } else {
                    throw new DAOException("模板有误，请确认！");
                }

            } drug = new DrugListMatch();
            boolean flag = true;
            StringBuilder errMsg = new StringBuilder();
            /*try{*/
            try {
                if (StringUtils.isEmpty(getStrFromCell(row.getCell(0)))) {
                    errMsg.append("药品编号不能为空").append(";");
                }
                drug.setOrganDrugCode(getStrFromCell(row.getCell(0)));
            } catch (Exception e) {
                errMsg.append("药品编号有误").append(";");
            }

            try {
                if (StringUtils.isEmpty(getStrFromCell(row.getCell(1)))) {
                    errMsg.append("药品通用名不能为空").append(";");
                }
                drug.setDrugName(getStrFromCell(row.getCell(1)));
            } catch (Exception e) {
                errMsg.append("药品通用名有误").append(";");
            }

            try {
                drug.setSaleName(getStrFromCell(row.getCell(2)));
            } catch (Exception e) {
                errMsg.append("药品商品名有误").append(";");
            }

            try {
                drug.setDrugSpec(getStrFromCell(row.getCell(3)));
            } catch (Exception e) {
                errMsg.append("药品规格有误").append(";");
            }
            try {
                if (("中药").equals(getStrFromCell(row.getCell(4)))) {
                    drug.setDrugType(3);
                } else if (("中成药").equals(getStrFromCell(row.getCell(4)))) {
                    drug.setDrugType(2);
                } else if (("西药").equals(getStrFromCell(row.getCell(4)))) {
                    drug.setDrugType(1);
                } else {
                    errMsg.append("药品类型格式错误").append(";");
                }
            } catch (Exception e) {
                errMsg.append("药品类型有误").append(";");
            }

            try {
                if (StringUtils.isEmpty(getStrFromCell(row.getCell(5)))) {
                    errMsg.append("单次剂量不能为空").append(";");
                } else {
                    drug.setUseDose(Double.parseDouble(getStrFromCell(row.getCell(5))));
                }
            } catch (Exception e) {
                errMsg.append("单次剂量有误").append(";");
            }
            try {
                if (StringUtils.isEmpty(getStrFromCell(row.getCell(6)))) {
                    drug.setDefaultUseDose(null);
                } else {
                    drug.setDefaultUseDose(Double.parseDouble(getStrFromCell(row.getCell(6))));
                }
            } catch (Exception e) {
                errMsg.append("默认单次剂量有误").append(";");
            }
            try {
                drug.setUseDoseUnit(getStrFromCell(row.getCell(7)));
            } catch (Exception e) {
                errMsg.append("剂量单位有误").append(";");
            }
            try {
                if (StringUtils.isEmpty(getStrFromCell(row.getCell(8)))) {
                    errMsg.append("转换系数不能为空").append(";");
                } else {
                    drug.setPack(Integer.parseInt(getStrFromCell(row.getCell(8))));
                }
            } catch (Exception e) {
                errMsg.append("转换系数有误").append(";");
            }

            try {
                if (StringUtils.isEmpty(getStrFromCell(row.getCell(9)))) {
                    errMsg.append("药品单位不能为空").append(";");
                }
                drug.setUnit(getStrFromCell(row.getCell(9)));
            } catch (Exception e) {
                errMsg.append("药品单位有误").append(";");
            }
            try {
                if (StringUtils.isEmpty(getStrFromCell(row.getCell(10)))) {
                    errMsg.append("生产厂家不能为空").append(";");
                }
                drug.setProducer(getStrFromCell(row.getCell(10)));
            } catch (Exception e) {
                errMsg.append("生产厂家有误").append(";");
            }
            try {
                String priceCell = getStrFromCell(row.getCell(11));
                if (StringUtils.isEmpty(priceCell)) {
                    drug.setPrice(new BigDecimal(0));
                } else {
                    drug.setPrice(new BigDecimal(priceCell));
                }
            } catch (Exception e) {
                errMsg.append("药品单价有误").append(";");
            }
            drug.setLicenseNumber(getStrFromCell(row.getCell(12)));
            drug.setStandardCode(getStrFromCell(row.getCell(13)));
            drug.setIndications(getStrFromCell(row.getCell(14)));
            drug.setDrugForm(getStrFromCell(row.getCell(15)));
            drug.setPackingMaterials(getStrFromCell(row.getCell(16)));
            try {
                if (("是").equals(getStrFromCell(row.getCell(17)))) {
                    drug.setBaseDrug(1);
                } else if (("否").equals(getStrFromCell(row.getCell(17)))) {
                    drug.setBaseDrug(0);
                }else {
                    errMsg.append("是否基药格式不正确").append(";");
                }

            } catch (Exception e) {
                errMsg.append("是否基药有误").append(";");
            }
            try {
                drug.setRetrievalCode(getStrFromCell(row.getCell(18)));
            } catch (Exception e) {
                errMsg.append("院内检索码有误").append(";");
            }
            drug.setSourceOrgan(organId);
            drug.setStatus(0);
            drug.setOperator(operator);

            if (errMsg.length() > 1) {
                String error = ("【第" + rowIndex+1 + "行】" + errMsg.substring(0, errMsg.length() - 1));
                errDrugListMatchList.add(error);
            }else {
                try {
                    boolean isSuccess = drugListMatchDAO.updateData(drug);
                    if (!isSuccess) {
                        //自动匹配功能暂无法提供
                        //*AutoMatch(drug);*//*
                        drugListMatchDAO.save(drug);
                    }
                } catch (Exception e) {
                    LOGGER.error("save or update drugListMatch error " + e.getMessage());
                }
            }
            progress = new BigDecimal((float) rowIndex / total).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
            redisClient.set(organId + operator, progress * 100);
//                    progressMap.put(organId+operator,progress*100);
        }

        if (errDrugListMatchList.size()>0){
            result.put("code", 609);
            result.put("msg", errDrugListMatchList);
        }

        LOGGER.info(operator + "结束 readDrugExcel 方法" + System.currentTimeMillis() + "当前进程=" + Thread.currentThread().getName());
        result.put("code", 200);
        return result;
    }


    /*private void AutoMatch(DrugListMatch drug) {
        List<DrugList> drugLists = drugListDAO.findByDrugName(drug.getDrugName());
        if (CollectionUtils.isNotEmpty(drugLists)){
            for (DrugList drugList : drugLists){
                if (drugList.getPack().equals(drug.getPack())
                        &&(drugList.getProducer().equals(drug.getProducer()))
                        &&(drugList.getUnit().equals(drug.getUnit()))
                        &&(drugList.getUseDose().equals(drug.getUseDose()))
                        &&(drugList.getDrugType().equals(drug.getDrugType()))){
                    drug.setStatus(1);
                    drug.setMatchDrugId(drugList.getDrugId());
                }
            }
        }
    }*/

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
        return drugListMatchDAO.findMatchDataByOrgan(organId, start, limit);
    }

    /**
     * 更新无匹配数据
     */
    @RpcService
    public void updateNoMatchData(int drugId, String operator) {
        if (StringUtils.isEmpty(operator)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "operator is required");
        }
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);
        //如果是已匹配的取消匹配
        if (drugListMatch.getStatus().equals(1)) {
            drugListMatchDAO.updateDrugListMatchInfoById(drugId, ImmutableMap.of("status", 0, "operator", operator));
        }
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, ImmutableMap.of("isNew", 1, "status", 3, "operator", operator));
        LOGGER.info("updateNoMatchData 操作人->{}更新无匹配数据,drugId={};status ->before={},after=3", operator, drugId, drugListMatch.getStatus());
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
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, ImmutableMap.of("status", 0, "operator", operator));
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
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, ImmutableMap.of("status", 1, "matchDrugId", matchDrugId, "operator", operator));
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
            LOGGER.error("findOrganByRecipeTools 药品小工具查询所有机构接口异常");
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
        LOGGER.info("findOrganSearchHistoryRecord HistoryRecord  queue{}==", queue.toString());
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
    public List<DrugListBean> drugMatch(int drugId) {
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);

        String str = DrugMatchUtil.match(drugListMatch.getDrugName());
        //根据药品名取标准药品库查询相关药品
        List<DrugList> drugLists = null;
        List<DrugListBean> drugListBeans = null;
        try {
            drugLists = drugListCache.get(str);
        } catch (ExecutionException e) {
            LOGGER.error("drugMatch:" + e.getMessage());
        }

        //已匹配状态返回匹配药品id
        if (CollectionUtils.isNotEmpty(drugLists)) {
            drugListBeans = ObjectCopyUtils.convert(drugLists, DrugListBean.class);
            if (drugListMatch.getStatus().equals(1) || drugListMatch.getStatus().equals(2)) {
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
    public Map<String, Integer> drugManualCommit(final int organId) {
        List<DrugListMatch> matchDataByOrgan = drugListMatchDAO.findMatchDataByOrgan(organId);
        final HibernateStatelessResultAction<Integer> action = new AbstractHibernateStatelessResultAction<Integer>() {
            @SuppressWarnings("unchecked")
            @Override
            public void execute(StatelessSession ss) throws Exception {
                List<DrugListMatch> lists = drugListMatchDAO.findDataByOrganAndStatus(organId, 2);
                int num = 0;
                //更新数据到organDrugList并更新状态已提交
                for (DrugListMatch drugListMatch : lists) {
                    if (drugListMatch.getStatus().equals(2) && drugListMatch.getMatchDrugId() != null) {
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
                        organDrugList.setProducer(drugListMatch.getProducer());
                        organDrugList.setUseDose(drugListMatch.getDefaultUseDose());
                        organDrugList.setRecommendedUseDose(drugListMatch.getUseDose());
                        organDrugList.setPack(drugListMatch.getPack());
                        organDrugList.setUnit(drugListMatch.getUnit());
                        organDrugList.setUseDoseUnit(drugListMatch.getUseDoseUnit());
                        organDrugList.setDrugSpec(drugListMatch.getDrugSpec());
                        organDrugList.setRetrievalCode(drugListMatch.getRetrievalCode());
                        organDrugList.setDrugForm(drugListMatch.getDrugForm());
                        organDrugList.setBaseDrug(drugListMatch.getBaseDrug());
                        organDrugList.setRegulationDrugCode(drugListMatch.getRegulationDrugCode());
                        organDrugList.setTakeMedicine(0);
                        organDrugList.setStatus(1);
                        organDrugList.setProducerCode("");
                        organDrugList.setLastModify(new Date());
                        Boolean isSuccess = organDrugListDAO.updateData(organDrugList);
                        if (!isSuccess) {
                            organDrugListDAO.save(organDrugList);
                            num = num + 1;
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
    @RpcService
    public void drugCommit(final List<DrugListMatch> lists) {
        for (DrugListMatch drugListMatch : lists) {
            if (drugListMatch.getStatus().equals(1) && drugListMatch.getMatchDrugId() != null) {
                drugListMatch.setStatus(2);
                drugListMatchDAO.update(drugListMatch);
            }
        }
    }

    /**
     * 药品搜索(可根据药品名称，厂家等进行搜索)
     */
    @RpcService
    public QueryResult<DrugListMatch> drugSearch(int organId, String keyWord, Integer status, int start, int limit) {
        return drugListMatchDAO.queryDrugListsByDrugNameAndStartAndLimit(organId, keyWord, status, start, limit);
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
            LOGGER.error("getUsingRateAndUsePathway() error : " + e);
        }
        result.put("usingRate", usingRateList);
        result.put("usePathway", usePathwayList);
        return result;
    }

    @RpcService
    public void deleteDrugMatchData(Integer id, Boolean isOrganId) {
        if (isOrganId) {
            drugListMatchDAO.deleteByOrganId(id);
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
     * 上传未匹配数据到通用药品目录
     *
     * @param organId       机构id
     * @param isHaveOrganId 通用药品目录是否包含机构来源
     * @return
     */
    @RpcService
    public Integer uploadNoMatchData(Integer organId, Boolean isHaveOrganId) {
        List<DrugListMatch> data = drugListMatchDAO.findDataByOrganAndStatus(organId, 3);
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
                drugList.setStatus(1);
                //来源机构
                if (isHaveOrganId) {
                    drugList.setSourceOrgan(organId);
                }
                drugListDAO.save(drugList);
            }
            return data.size();
        }
        return 0;
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
        String addrArea = checkOrganAddrArea(organId);
        Long provinceDrugNum = provinceDrugListDAO.getCountByProvinceIdAndStatus(addrArea, 1);
        //更新药品状态成匹配中
        if(0L < provinceDrugNum){
            //批量将匹配药品状态设置成匹配中
            drugListMatchDAO.updateStatusListToStatus(Arrays.asList(Change_Matching_StatusList), organId, DrugMatchConstant.MATCHING);
        }
    }

    private String checkOrganAddrArea(int organId) {
        //查询是否有意需要更新状态的药品（将匹配药品中已匹配的和已提交）
        //这里直接判断机构对应省药品有无药品
        OrganDTO organDTO = organService.get(organId);
        if(null == organDTO){
            LOGGER.warn("updateOrganDrugMatchByProvinceDrug 当期机构[{}]不存在", organId);
            return null;
        }
        String addrArea = organDTO.getAddrArea();
        //校验省平台的地址信息合理性
        if(null == addrArea || 2 > addrArea.length()){
            LOGGER.error("updateOrganDrugMatchByProvinceDrug() error : 医院[{}],对应的省信息不全", organId);
            throw new DAOException(DAOException.VALUE_NEEDED, "医院对应的省信息不全");
        }
        return addrArea.substring(0, 2);
    }

    /**
     * @method  updateMatchStatusCurrent
     * @description 选中的平台药品/省平台药品匹配机构药品
     * @date: 2019/10/25
     * @author: JRK
     * @param updateMatchStatusFormBean 属性值
     * drugId 更新的药品id
     * matchDrugId 匹配上的平台药品id
     * matchDrugInfo 匹配上省平台的药品code
     * operator 操作人
     * haveProvinceDrug 是否有当前机构对应省的药品（由前端查询list是否为空判断）
     * @return void
     */
    @RpcService
    public void updateMatchStatusCurrent(UpdateMatchStatusFormBean updateMatchStatusFormBean) {
        String operator = updateMatchStatusFormBean.getOperator();
        if (StringUtils.isEmpty(operator)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "operator is required");
        }

        //更新状态数据准备
        Integer drugId = updateMatchStatusFormBean.getDrugId();
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);
        Integer drugListMatchStatus = drugListMatch.getStatus();
        if(null == drugListMatch){
            LOGGER.warn("updateMatchStatusCurrent 当期对照药品[{}]不存在", drugId);
            return;
        }
        int makeType = updateMatchStatusFormBean.getMakeType();
        Boolean haveProvinceDrug = updateMatchStatusFormBean.getHaveProvinceDrug();
        Integer matchDrugId = updateMatchStatusFormBean.getMatchDrugId();
        String matchDrugInfo = updateMatchStatusFormBean.getMatchDrugInfo();


        Map<String, Object> updateMap = new HashMap<>();
        //判断是否有省平台药品对应
        Integer status = 0;
        if(Platform_Type == makeType){
            //平台匹配操作
            if(haveProvinceDrug){
                //匹配省平台的时候
                status = geUpdateStatus(drugId, drugListMatchStatus, "updateMatchStatusCurrent 当前匹配药品[{}]状态[{}]不能进行平台匹配");
                if (status == null) return;
            }else{
                //无匹省台的时候
                status = DrugMatchConstant.ALREADY_MATCH;
            }
            updateMap.put("matchDrugId", matchDrugId);
        }else if (Province_Platform_Type == makeType){
            //省平台匹配操作
            status = geUpdateStatus(drugId, drugListMatchStatus, "updateMatchStatusCurrent 当前匹配药品[{}]状态[{}]不能进行省平台匹配");
            if (status == null) return;
            updateMap.put("regulationDrugCode", matchDrugInfo);
        }else{
            LOGGER.info("updateMatchStatusCurrent 传入操作状态非平台和省平台", makeType);
            return;
        }
        updateMap.put("operator", operator);
        updateMap.put("status", status);
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, updateMap);
        LOGGER.info("updateMatchStatusCurrent 操作人->{}更新已匹配状态,drugId={};status ->before={},after={}", operator, drugId, drugListMatch.getStatus(), status);
    }

    /*获取更新后的对照状态状态*/
    private Integer geUpdateStatus(Integer drugId, Integer drugListMatchStatus, String message) {
        Integer status;
        if(DrugMatchConstant.UNMATCH == drugListMatchStatus){
            status = DrugMatchConstant.MATCHING;
        }else if(DrugMatchConstant.MATCHING == drugListMatchStatus){
            status = DrugMatchConstant.ALREADY_MATCH;
        }else{
            LOGGER.info(message, drugId, drugListMatchStatus);
            return null;
        }
        return status;
    }

    /**
     * 省药品匹配
     */
    @RpcService
    public List<ProvinceDrugListBean> provinceDrugMatch(int drugId, int organId) {
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);

        if(null == drugListMatch){
            LOGGER.warn("provinceDrugMatch 当期药品[{}]不在机构对照列表中", drugId);
            return null;
        }
        List<ProvinceDrugList> provinceDrugLists = getProvinceDrugLists(organId, drugListMatch);
        List<ProvinceDrugListBean> provinceDrugListBeans = getProvinceDrugListBean(drugListMatch, provinceDrugLists);

        return provinceDrugListBeans;

    }

    /*根据匹配的药品销售名，获取相似名称的省平台药品*/
    private List<ProvinceDrugList> getProvinceDrugLists(int organId, DrugListMatch drugListMatch) {
        String addrArea = checkOrganAddrArea(organId);
        String likeDrugName = DrugMatchUtil.match(drugListMatch.getDrugName());
        //根据药品名取标准药品库查询相关药品
        List<ProvinceDrugList> provinceDrugLists = null;
        try {
            provinceDrugLists = provinceDrugListCache.get(addrArea+ "-" + likeDrugName);
        } catch (ExecutionException e) {
            LOGGER.error("drugMatch:" + e.getMessage());
        }
        return provinceDrugLists;
    }

    /*渲染页面上的勾选展示的项*/
    private List<ProvinceDrugListBean> getProvinceDrugListBean(DrugListMatch drugListMatch, List<ProvinceDrugList> provinceDrugLists) {
        List<ProvinceDrugListBean> provinceDrugListBeans = null;
        //已匹配状态返回匹配药品id
        if (CollectionUtils.isNotEmpty(provinceDrugLists)) {
            provinceDrugListBeans = ObjectCopyUtils.convert(provinceDrugLists, ProvinceDrugListBean.class);
            if (drugListMatch.getStatus().equals(DrugMatchConstant.ALREADY_MATCH) || drugListMatch.getStatus().equals(DrugMatchConstant.SUBMITED)) {
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
    public void cancelMatchStatusByOrgan(Integer drugId, String operator, Integer makeType, Boolean haveProvinceDrug) {
        if (StringUtils.isEmpty(operator)) {
            throw new DAOException(DAOException.VALUE_NEEDED, "operator is required");
        }
        Map<String, Object> updateMap = new HashMap<>();
        DrugListMatch drugListMatch = drugListMatchDAO.get(drugId);
        Integer status = 0;
        if(Platform_Type == makeType){
            if(haveProvinceDrug){
                status = getCancelStatus(drugId, drugListMatch.getStatus(), "cancelMatchStatusByOrgan 当前匹配药品[{}]状态[{}]不能取消平台匹配");
                if (status == null) return;
            }else{
                status = DrugMatchConstant.UNMATCH;
            }
            updateMap.put("matchDrugId", null);
        }else if(Province_Platform_Type == makeType){
            status = getCancelStatus(drugId, drugListMatch.getStatus(), "cancelMatchStatusByOrgan 当前匹配药品[{}]状态[{}]不能取消省平台匹配");
            if (status == null) return;
            updateMap.put("regulationDrugCode", null);
        }else{
            LOGGER.info("cancelMatchStatusByOrgan 传入操作状态非平台和省平台", makeType);
            return;
        }
        updateMap.put("status", status);
        updateMap.put("operator", operator);
        drugListMatchDAO.updateDrugListMatchInfoById(drugId, updateMap);
        LOGGER.info("cancelMatchStatusByOrgan 操作人取消关联->{}更新状态,drugId={};status ->before={},after={}", operator, drugId, drugListMatch.getStatus(), status);
    }

    /*获取取消匹配后的对照状态状态*/
    private Integer getCancelStatus(Integer drugId, Integer drugListMatchStatus, String message) {
        Integer status;
        if(DrugMatchConstant.ALREADY_MATCH == drugListMatchStatus){
            status = DrugMatchConstant.MATCHING;
        }else if(DrugMatchConstant.MATCHING == drugListMatchStatus){
            status = DrugMatchConstant.UNMATCH;
        }else{
            LOGGER.info(message, drugId, drugListMatchStatus);
            return null;
        }
        return status;
    }


}
