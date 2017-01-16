package demo.cd.be.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import demo.cd.be.model.DeployInfo;
import demo.cd.be.model.Quotorama;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/info")
@Slf4j
public class InfoController {

    @Autowired
    private DeployInfo deployInfo;

    @Autowired
    private Quotorama quoter;

    @RequestMapping(method = RequestMethod.GET)
    public Map<String, Object> collectInfo() throws Exception {
        log.info("Backend collectInfo start");
        Map<String, Object> info = deployInfo.getData();
        info.put("quote", quoter.nextQuote("2"));
        return info;
    }

    @RequestMapping(value = "/html", method = RequestMethod.GET)
    public String collectInfoAsHtml() throws Exception {
        log.info("Backend collectInfoAsHtml start");

        Map<String, Object> info = collectInfo();

        StringBuilder res = new StringBuilder();
        res.append("<html><body>");
        res.append("  <pre>");

        ObjectMapper mapper = new ObjectMapper();
        res.append(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(info));

        res.append("  </pre>");
        res.append("</body></html>");
        return res.toString();
    }

}
