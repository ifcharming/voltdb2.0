//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.08.25 at 03:25:44 PM EDT 
//


package org.voltdb.benchmark.workloads.xml;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for tinyintParamType complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="tinyintParamType">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;attribute name="min" type="{http://www.w3.org/2001/XMLSchema}byte" default="-128" />
 *       &lt;attribute name="max" type="{http://www.w3.org/2001/XMLSchema}byte" default="127" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "tinyintParamType")
public class TinyintParamType {

    @XmlAttribute
    protected Byte min;
    @XmlAttribute
    protected Byte max;

    /**
     * Gets the value of the min property.
     * 
     * @return
     *     possible object is
     *     {@link Byte }
     *     
     */
    public byte getMin() {
        if (min == null) {
            return ((byte)-128);
        } else {
            return min;
        }
    }

    /**
     * Sets the value of the min property.
     * 
     * @param value
     *     allowed object is
     *     {@link Byte }
     *     
     */
    public void setMin(Byte value) {
        this.min = value;
    }

    /**
     * Gets the value of the max property.
     * 
     * @return
     *     possible object is
     *     {@link Byte }
     *     
     */
    public byte getMax() {
        if (max == null) {
            return ((byte) 127);
        } else {
            return max;
        }
    }

    /**
     * Sets the value of the max property.
     * 
     * @param value
     *     allowed object is
     *     {@link Byte }
     *     
     */
    public void setMax(Byte value) {
        this.max = value;
    }

}
