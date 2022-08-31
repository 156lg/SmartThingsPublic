/////////////////////////
// Zigbee Multi Dimmer
/////////////////////////
    
import physicalgraph.zigbee.zcl.DataType

private getMODEL_MAP() { 
    [
        'IDSW1O': 1,
        'IDSW2O': 2
    ]
}

metadata {
	definition (name: "Interfree Multi Dimmer", namespace: "Interfree", author: "Rana Afzal, Moe", ocfDeviceType: "oic.d.light", runLocally: false, minHubCoreVersion: '000.019.00012', executeCommandsLocally: true, genericHandler: "Zigbee") {
		capability "Actuator"
		capability "Configuration"
		capability "Refresh"
		capability "Switch"
		capability "Switch Level"
		capability "Health Check"
		capability "Light"
        
		command "childOn", ["string"]
		command "childOff", ["string"]
		command "childSetLevel", ["string", "string"]

		// Generic
		//fingerprint profileId: "0104", deviceId: "0101", inClusters: "0006, 0008", deviceJoinName: "Light" //Generic Dimmable Light
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0006, 0008, 0019", manufacturer: "Interfree", model: "IDSW2O", deviceJoinName: "Interfree 2 Gang I1"// 2路调光
        
        fingerprint profileId: "0104", inClusters: "0000, 0003, 0004, 0005, 0006, 0008", outClusters: "0006, 0008, 0019", manufacturer: "Interfree", model: "IDSW1O", deviceJoinName: "Interfree 1 Gang I1"// 单路调光
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
		}
		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		}
		main "switch"
		details(["switch", "refresh"])
	}
}

def installed() {
	createChildDevices()
	updateDataValue("onOff", "catchall")
	refresh()
}

// Parse incoming device messages to generate events
def parse(String description) {

	Map eventMap = zigbee.getEvent(description)
	Map eventDescMap = zigbee.parseDescriptionAsMap(description)

	log.debug "description is $description"

	if (eventMap) {
		if (eventDescMap && eventDescMap?.attrId == "0000") {//0x0000 : OnOff attributeId
			if (eventDescMap?.sourceEndpoint == "01" || eventDescMap?.endpoint == "01") {
				sendEvent(eventMap)
			} else {
				def childDevice = childDevices.find {
					it.deviceNetworkId == "$device.deviceNetworkId:${eventDescMap.sourceEndpoint}" || it.deviceNetworkId == "$device.deviceNetworkId:${eventDescMap.endpoint}"
				}
				if (childDevice) {
					childDevice.sendEvent(eventMap)
				} else {
					log.debug "Child device: $device.deviceNetworkId:${eventDescMap.sourceEndpoint} was not found"
				}
			}
		}
	} else {
		def descMap = zigbee.parseDescriptionAsMap(description)
		if (descMap && descMap.clusterInt == 0x0006 && descMap.commandInt == 0x07) {
			if (descMap.data[0] == "00") {
				log.debug "ON/OFF REPORTING CONFIG RESPONSE: " + cluster
				sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				log.warn "ON/OFF REPORTING CONFIG FAILED- error code:${cluster.data[0]}"
			}
		} else {
			log.warn "DID NOT PARSE MESSAGE for description : $description"
			log.debug "${descMap}"
		}
	}
}

private getChildEndpoint(String dni) {
	dni.split(":")[-1] as Integer
}

def off() {
	log.debug("off")
    zigbee.off()
}

def on() {
	log.debug("on")
    zigbee.on()
}

def childOn(String dni) {
	def childEndpoint = getChildEndpoint(dni)
    log.debug(" child on ${dni} ${childEndpoint} ")
	zigbee.command(zigbee.ONOFF_CLUSTER, 0x01, "", [destEndpoint: childEndpoint])
}

def childOff(String dni) {
	def childEndpoint = getChildEndpoint(dni)
    log.debug(" child off ${dni} ${ childEndpoint} ")
	zigbee.command(zigbee.ONOFF_CLUSTER, 0x00, "", [destEndpoint: childEndpoint])
}

def setOnLevel (endpoint, level) {
	log.debug "setOnLevel($endpoint, $level)"
	zigbee.writeAttribute(zigbee.LEVEL_CONTROL_CLUSTER, 0x0011, DataType.UINT8, level, [destEndpoint: endpoint])
}

def moveToLevel (endpoint, level, transitionTime) {
	log.debug "moveToLevel($endpoint, $level, $transitionTime)"
    def p1 = DataType.pack(level, DataType.UINT8, false)
    def p2 = DataType.pack(transitionTime, DataType.UINT16, false)
    def dataString = [p1, p2].join(" ")
    //"st cmd 0x${device.deviceNetworkId} ${endpointId} 8 4 {${scaledLevel} ${transitionTime}}"
    zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x0000, dataString, [destEndpoint: endpoint]) +
    zigbee.command(zigbee.LEVEL_CONTROL_CLUSTER, 0x0004, dataString, [destEndpoint: endpoint])
}

def childSetLevel (String dni, Integer value) {
	def endpoint = getChildEndpoint(dni)
    def rate = 5;
	log.debug "setLevel($endpoint, $value, $rate)"
    def level = Math.round(Math.floor(value * 2.54))
    setOnLevel(endpoint, level) + moveToLevel(endpoint, level, rate)
}

def setLevel(value, rate = null) {
	def additionalCmds = []
	zigbee.setLevel(value) + additionalCmds
}
/**
 * PING is used by Device-Watch in attempt to reach the Device
 * */
def ping() {
	return zigbee.onOffRefresh()
}

def refresh() {
	zigbee.onOffRefresh() + zigbee.levelRefresh()
}

private void createChildDevices() {
	if (!childDevices) {
		def x = getChildCount()
       	log.debug(" childCount ${x}")

		if(x > 1)// Interfree Child Switch Dimmer      Child Switch Dimmer
        {
            for (i in 2..x) {
                addChildDevice("Interfree Child Switch Dimmer", "${device.deviceNetworkId}:0${i}", device.hubId,
                    [completedSetup: true, label: "${device.displayName[0..-2]}${i}", isComponent: false])
                log.debug(" Device ${i} created")

            }
        }
	}
}

def updated() {
	log.debug "updated()"
	updateDataValue("onOff", "catchall")
	for (child in childDevices) {
		if (!child.deviceNetworkId.startsWith(device.deviceNetworkId) || //parent DNI has changed after rejoin
				!child.deviceNetworkId.split(':')[-1].startsWith('0')) {
			child.setDeviceNetworkId("${device.deviceNetworkId}:0${getChildEndpoint(child.deviceNetworkId)}")
		}
	}
	refresh()
}

def configureHealthCheck() {
	Integer hcIntervalMinutes = 12
	if (!state.hasConfiguredHealthCheck) {
		log.debug "Configuring Health Check, Reporting"
		unschedule("healthPoll")
		runEvery5Minutes("healthPoll")
		def healthEvent = [name: "checkInterval", value: hcIntervalMinutes * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID]]
		// Device-Watch allows 2 check-in misses from device
		sendEvent(healthEvent)
		childDevices.each {
			it.sendEvent(healthEvent)
		}
		state.hasConfiguredHealthCheck = true
	}
}

def configure() {
	log.debug "configure()"
	configureHealthCheck()
	   
    def cmds = zigbee.onOffConfig(0, 300) + zigbee.levelConfig()
	def x = getChildCount()
	for (i in 2..x) {
		cmds += zigbee.configureReporting(zigbee.ONOFF_CLUSTER, 0x0000, 0x10, 0, 120, null, [destEndpoint: i])
	}
	cmds += refresh()
	return cmds
}

private getChildCount() {

def model = device.getDataValue("model")
    def count = MODEL_MAP[model] ?: 0
    log.debug("getEndpointCount[$model] : $count")
    return count
    
	//return 2
}