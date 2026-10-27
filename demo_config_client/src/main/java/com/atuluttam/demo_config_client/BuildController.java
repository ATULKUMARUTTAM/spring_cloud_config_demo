package com.atuluttam.demo_config_client;

import lombok.AllArgsConstructor;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RefreshScope
public class BuildController
{
    private final BuildDemo buildDemo;

    @GetMapping("/buildinfo")
    public String getBuildInfo()
    {
        return "Build ID: " + buildDemo.getId() + ", Version: " + buildDemo.getVersion() + ", Name: " + buildDemo.getName();
    }
}
