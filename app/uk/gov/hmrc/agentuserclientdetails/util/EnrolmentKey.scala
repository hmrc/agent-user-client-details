package uk.gov.hmrc.agentuserclientdetails.util

object EnrolmentKey {
  def enrolmentKey(serviceId: String, clientId: String): String = serviceId match {
    case "HMRC-MTD-IT"     => "HMRC-MTD-IT~MTDITID~" + clientId
    case "HMRC-MTD-VAT"    => "HMRC-MTD-VAT~VRN~" + clientId
    case "HMRC-MTD-IT"     => "HMRC-MTD-IT~NINO~" + clientId
    case "HMRC-TERS-ORG"   => "HMRC-TERS-ORG~SAUTR~" + clientId
    case "HMRC-TERSNT-ORG" => "HMRC-TERSNT-ORG~URN~" + clientId
    case "HMRC-CGT-PD"     => "HMRC-CGT-PD~CGTPDRef~" + clientId
    case "HMRC-PPT-ORG"    => "HMRC-PPT-ORG~EtmpRegistrationNumber~" + clientId
    case "HMRC-PT"         => "HMRC-PT~NINO~" + clientId // TODO Check: is this correct for HMRC-PT (IRV)?
    case _                 => throw new IllegalArgumentException(s"Service not supported: $serviceId")
  }

}
