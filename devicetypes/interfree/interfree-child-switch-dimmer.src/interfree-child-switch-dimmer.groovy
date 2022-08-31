/////////////////////////
// Child Switch Dimmer
/////////////////////////
    
metadata {
	definition(name: "Interfree Child Switch Dimmer", namespace: "Interfree", author: "Rana Afzal, Moe", ocfDeviceType: "oic.d.light", runLocally: false, minHubCoreVersion: '000.019.00012', executeCommandsLocally: true, genericHandler: "Zigbee") {
		capability "Actuator"
		capability "Refresh"
		capability "Switch"
		capability "Switch Level"
		capability "Light"
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
    sendEvent(name: "checkInterval", value: 30 * 60, displayed: false, data: [protocol: "zigbee"])
    log.debug("installed")
}

// Parse incoming device messages to generate events
def parse(String description) {
    log.debug("parse")
}

def ping() {
    log.debug("ping")
}

def configure() {
    log.debug("configure")
}

def setLevel(value, rate = null) {
    log.debug("level ${value}  ")
	parent.childSetLevel(device.deviceNetworkId, value)
}


void on() {
    log.debug("Local child on ${device.deviceNetworkId}")
	parent.childOn(device.deviceNetworkId)
}

void off() {
    log.debug("Local child off ${device.deviceNetworkId}")
	parent.childOff(device.deviceNetworkId)
}

void refresh() {
    log.debug("Local child refresh ${device.deviceNetworkId}")
}