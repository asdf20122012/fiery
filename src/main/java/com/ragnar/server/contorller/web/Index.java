package com.ragnar.server.contorller.web;

import com.ragnar.server.Processor.BizLogProcessor;
import com.ragnar.server.Processor.MetaLogProcessor;
import com.ragnar.server.data.statics.APITopURLStaticShardCollect;
import com.ragnar.server.search.IndexService;
import com.ragnar.server.statistics.ErrorStatics;
import com.ragnar.server.statistics.LogAPIStatics;
import com.ragnar.server.statistics.SQLStatics;
import com.ragnar.server.storage.DBManage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Map;

@Controller
public class Index {

    @Autowired
    IndexService indexService;

    @Autowired
    DBManage dbManage;

    @Autowired
    ErrorStatics errorStatics;

    @Autowired
    LogAPIStatics logAPIStatics;

    @Autowired
    SQLStatics sqlStatics;

    @Autowired
    APITopURLStaticShardCollect apiTopURLStaticShardCollect;

    @Autowired
    BizLogProcessor bizLogProcessor;

    @Autowired
    MetaLogProcessor metaLogProcessor;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    public String IndexPage(Model model) {

        //write index list
        Map<String, Map<String, String>> writeIndexInfo = indexService.getWriteIndexInfo();
        model.addAttribute("writeInfoList", writeIndexInfo);

        //search index list
        Map<String, Map<String, String>> searchIndexInfo = indexService.getSearchIndexInfo();
        model.addAttribute("searchInfoList", searchIndexInfo);

        //multisearch index doc count
        int indexedDocCount = indexService.getIndexedDocCount();
        model.addAttribute("indexedDocCount", indexedDocCount);

        //db list
        Map<String, String> dbInfoList = dbManage.getDbList();
        model.addAttribute("dbInfoList", dbInfoList);

        //error statics
        Map<String, Integer> errorStatic = errorStatics.getErrorStatics();
        model.addAttribute("errorStatic", errorStatic);

        //alarm statics
        Map<String, Integer> alarmStatic = errorStatics.getAlaramStatics();
        model.addAttribute("alarmStatic", alarmStatic);

        //exception statics
        Map<String, Integer> exceptionStatic = errorStatics.getExceptionStatics();
        model.addAttribute("exceptionStatic", exceptionStatic);

        //api top statics
        Map<String, Integer> apitopStatic = apiTopURLStaticShardCollect.getAPITOPStatics();
        model.addAttribute("apitopStatic", apitopStatic);

        model.addAttribute("metalogQueueLen", metaLogProcessor.getQueueLen());

        model.addAttribute("bizlogQueueLen", bizLogProcessor.getQueueLen());

        return "index";
    }
}
