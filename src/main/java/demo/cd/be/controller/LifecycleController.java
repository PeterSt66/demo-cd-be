package demo.cd.be.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.stereotype.*;
import org.springframework.ui.*;

import java.util.*;
import java.io.*;

import java.net.*;
import javax.servlet.http.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/lc")
public class LifecycleController {
    private static final Logger LOG = LoggerFactory.getLogger(LifecycleController.class);
    
    private static final int SLEEP_BEFORE_KILL = 500;
    private static final int HTTP_OK=200;
  
    @RequestMapping(value = "/ping", method = RequestMethod.GET)
    public String ping(HttpServletResponse resp) throws Exception {
        LOG.trace("ping!");
        return ok(resp);
    }

    @RequestMapping(value = "/stop", method = RequestMethod.GET)
    public String stopThis(HttpServletResponse resp) throws Exception {
        LOG.error("Received stop signal - exploding in 1.5 secs");
        new WaitBeforeKill().start();
        return ok(resp);
    }
        
    public String ok(HttpServletResponse resp) {
        resp.setStatus(HTTP_OK);
        return "OK";
    }
    
    
    class WaitBeforeKill extends Thread {
        public void run() {
            try {
                Thread.sleep(SLEEP_BEFORE_KILL);
                System.exit(0);
            }
            catch(Throwable e) {
                LOG.warn("Kill BE: Could not sleep: "+e,e);
            }
            }
        }

}
