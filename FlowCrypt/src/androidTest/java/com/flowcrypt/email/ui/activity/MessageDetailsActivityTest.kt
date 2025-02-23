/*
 * © 2016-present FlowCrypt a.s. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.activity

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.IdlingPolicies
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.scrollToHolder
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.flowcrypt.email.R
import com.flowcrypt.email.TestConstants
import com.flowcrypt.email.api.email.EmailUtil
import com.flowcrypt.email.api.email.model.AttachmentInfo
import com.flowcrypt.email.api.email.model.IncomingMessageInfo
import com.flowcrypt.email.api.retrofit.response.model.DecryptErrorMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.GenericMsgBlock
import com.flowcrypt.email.api.retrofit.response.model.PublicKeyMsgBlock
import com.flowcrypt.email.database.entity.MessageEntity
import com.flowcrypt.email.junit.annotations.NotReadyForCI
import com.flowcrypt.email.matchers.CustomMatchers
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withDrawable
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withEmptyRecyclerView
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withPgpBadge
import com.flowcrypt.email.matchers.CustomMatchers.Companion.withRecyclerViewItemCount
import com.flowcrypt.email.model.KeyImportDetails
import com.flowcrypt.email.rules.AddPrivateKeyToDatabaseRule
import com.flowcrypt.email.rules.ClearAppSettingsRule
import com.flowcrypt.email.rules.RetryRule
import com.flowcrypt.email.rules.ScreenshotTestRule
import com.flowcrypt.email.ui.activity.base.BaseMessageDetailsActivityTest
import com.flowcrypt.email.ui.adapter.MsgDetailsRecyclerViewAdapter
import com.flowcrypt.email.ui.adapter.PgpBadgeListAdapter
import com.flowcrypt.email.util.DateTimeUtil
import com.flowcrypt.email.util.GeneralUtil
import com.flowcrypt.email.util.PrivateKeysManager
import com.flowcrypt.email.util.TestGeneralUtil
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.anything
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * @author Denis Bondarenko
 * Date: 3/13/19
 * Time: 4:32 PM
 * E-mail: DenBond7@gmail.com
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class MessageDetailsActivityTest : BaseMessageDetailsActivityTest() {
  private val addPrivateKeyToDatabaseRule = AddPrivateKeyToDatabaseRule()
  private val simpleAttInfo = TestGeneralUtil.getObjectFromJson(
    "messages/attachments/simple_att.json",
    AttachmentInfo::class.java
  )
  private val encryptedAttInfo = TestGeneralUtil.getObjectFromJson(
    "messages/attachments/encrypted_att.json",
    AttachmentInfo::class.java
  )
  private val pubKeyAttInfo = TestGeneralUtil.getObjectFromJson(
    "messages/attachments/pub_key.json",
    AttachmentInfo::class.java
  )

  @get:Rule
  var ruleChain: TestRule = RuleChain
    .outerRule(ClearAppSettingsRule())
    .around(addAccountToDatabaseRule)
    .around(addPrivateKeyToDatabaseRule)
    .around(RetryRule.DEFAULT)
    .around(activeActivityRule)
    .around(ScreenshotTestRule())

  @Test
  fun testReplyButton() {
    testStandardMsgPlaintext()
    onView(withId(R.id.layoutReplyButton))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    intended(hasComponent(CreateMessageActivity::class.java.name))
  }

  @Test
  fun testTopReplyButton() {
    testTopReplyAction(getResString(R.string.reply))
  }

  @Test
  fun testReplyAllButton() {
    testStandardMsgPlaintext()
    onView(withId(R.id.layoutReplyAllButton))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    intended(hasComponent(CreateMessageActivity::class.java.name))
  }

  @Test
  fun testFwdButton() {
    testStandardMsgPlaintext()
    onView(withId(R.id.layoutFwdButton))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    intended(hasComponent(CreateMessageActivity::class.java.name))
  }

  @Test
  fun testTopForwardButton() {
    testTopReplyAction(getResString(R.string.forward))
  }

  @Test
  fun testStandardMsgPlaintext() {
    baseCheck(
      getMsgInfo(
        "messages/info/standard_msg_info_plaintext.json",
        "messages/mime/standard_msg_info_plaintext.txt"
      )
    )
    onView(withId(R.id.tVTo))
      .check(matches(withText(getResString(R.string.to_receiver, getResString(R.string.me)))))
  }

  @Test
  fun testStandardMsgPlaintextWithOneAttachment() {
    baseCheckWithAtt(
      getMsgInfo(
        "messages/info/standard_msg_info_plaintext_with_one_att.json",
        "messages/mime/standard_msg_info_plaintext_with_one_att.txt", simpleAttInfo
      ), simpleAttInfo
    )
  }

  @Test
  fun testEncryptedMsgPlaintext() {
    baseCheck(
      getMsgInfo(
        "messages/info/encrypted_msg_info_text.json",
        "messages/mime/encrypted_msg_info_plain_text.txt"
      )
    )
  }

  @Test
  @NotReadyForCI
  //don't enable this one on CI. It takes too long
  fun testEncryptedBigInlineAtt() {
    IdlingPolicies.setIdlingResourceTimeout(3, TimeUnit.MINUTES)
    baseCheck(
      getMsgInfo(
        "messages/info/encrypted_msg_big_inline_att.json",
        "messages/mime/encrypted_msg_big_inline_att.txt"
      )
    )
  }

  @Test
  fun testDecryptionError_KEY_MISMATCH_MissingKeyErrorImportKey() {
    testMissingKey(
      getMsgInfo(
        "messages/info/encrypted_msg_info_text_with_missing_key.json",
        "messages/mime/encrypted_msg_info_text_with_missing_key.txt"
      )
    )

    intending(hasComponent(ComponentName(getTargetContext(), ImportPrivateKeyActivity::class.java)))
      .respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))

    onView(withId(R.id.buttonImportPrivateKey))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())

    PrivateKeysManager.saveKeyFromAssetsToDatabase(
      accountEntity = addAccountToDatabaseRule.account,
      keyPath = TestConstants.DEFAULT_SECOND_KEY_PRV_STRONG,
      passphrase = TestConstants.DEFAULT_STRONG_PASSWORD,
      sourceType = KeyImportDetails.SourceType.EMAIL
    )

    val incomingMsgInfoFixed =
      TestGeneralUtil.getObjectFromJson(
        "messages/info/encrypted_msg_info_text_with_missing_key_fixed.json",
        IncomingMessageInfo::class.java
      )
    checkWebViewText(incomingMsgInfoFixed?.text)

    PrivateKeysManager.deleteKey(
      addAccountToDatabaseRule.account,
      TestConstants.DEFAULT_SECOND_KEY_PRV_STRONG
    )
  }

  @Test
  fun testDecryptionError_KEY_MISMATCH_MissingPubKey() {
    testMissingKey(
      getMsgInfo(
        "messages/info/encrypted_msg_info_text_error_one_pub_key.json",
        "messages/mime/encrypted_msg_info_plain_text_error_one_pub_key.txt"
      )
    )
  }

  @Test
  fun testDecryptionError_FORMAT_BadlyFormattedMsg() {
    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_info_text_error_badly_formatted.json",
      "messages/mime/encrypted_msg_info_plain_text_error_badly_formatted.txt"
    ) ?: throw NullPointerException()

    assertThat(msgInfo, notNullValue())

    val details = msgInfo.msgEntity

    launchActivity(details)
    matchHeader(msgInfo)

    val block = msgInfo.msgBlocks?.get(1) as DecryptErrorMsgBlock
    val decryptError = block.decryptErr
    val formatErrorMsg = (getResString(
      R.string.decrypt_error_message_badly_formatted,
      getResString(R.string.app_name)
    ) + "\n\n" + decryptError?.details?.type + ": " + decryptError?.details?.message)

    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(containsString(formatErrorMsg))))

    testSwitch(block.content ?: "")
    matchReplyButtons(details)
  }

  @Test
  fun testDecryptionError_NO_MDC() {
    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_info_error_no_mdc.json",
      "messages/mime/encrypted_msg_info_error_no_mdc.txt"
    ) ?: throw NullPointerException()

    assertThat(msgInfo, notNullValue())

    val details = msgInfo.msgEntity

    launchActivity(details)
    matchHeader(msgInfo)

    val block = msgInfo.msgBlocks?.get(1) as DecryptErrorMsgBlock
    val decryptError = block.decryptErr
    val errorMsg = getResString(
      R.string.could_not_decrypt_message_due_to_error,
      decryptError?.details?.type.toString() + ": " + getResString(R.string.decrypt_error_message_no_mdc)
    )
    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(errorMsg)))
    testSwitch(block.content ?: "")
    matchReplyButtons(details)
  }

  @Test
  fun testMissingKeyErrorChooseSinglePubKey() {
    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_info_text_with_missing_key.json",
      "messages/mime/encrypted_msg_info_text_with_missing_key.txt"
    )

    testMissingKey(msgInfo)

    onView(withId(R.id.buttonSendOwnPublicKey))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    onView(withId(R.id.textViewMessage)).check(
      matches(
        withText(
          getQuantityString(
            R.plurals
              .tell_sender_to_update_their_settings, 1
          )
        )
      )
    )
    onView(withId(R.id.buttonOk))
      .check(matches(isDisplayed()))
      .perform(click())
    intended(hasComponent(CreateMessageActivity::class.java.name))
  }

  @Test
  fun testMissingKeyErrorChooseFromFewPubKeys() {
    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_info_text_with_missing_key.json",
      "messages/mime/encrypted_msg_info_text_with_missing_key.txt"
    )

    testMissingKey(msgInfo)

    onView(withId(R.id.buttonSendOwnPublicKey))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())

    PrivateKeysManager.saveKeyFromAssetsToDatabase(
      addAccountToDatabaseRule
        .account, TestConstants.DEFAULT_SECOND_KEY_PRV_STRONG,
      TestConstants.DEFAULT_STRONG_PASSWORD, KeyImportDetails.SourceType.EMAIL
    )


    val msg = getQuantityString(
      R.plurals
        .tell_sender_to_update_their_settings, 2
    )

    onView(withId(R.id.textViewMessage))
      .check(matches(withText(msg)))
    onData(anything())
      .inAdapterView(withId(R.id.listViewKeys))
      .atPosition(1)
      .perform(click())
    onView(withId(R.id.buttonOk))
      .check(matches(isDisplayed()))
      .perform(click())
    intended(hasComponent(CreateMessageActivity::class.java.name))
  }

  @Test
  fun testEncryptedMsgTextWithOneAttachment() {
    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_info_text_with_one_att.json",
      "messages/mime/encrypted_msg_info_plain_text_with_one_att.txt", encryptedAttInfo
    )
    baseCheckWithAtt(msgInfo, encryptedAttInfo)
  }

  @Test
  fun testEncryptedMsgPlaintextWithPubKey() {
    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_info_text_with_pub_key.json",
      "messages/mime/encrypted_msg_info_text_with_pub_key.txt", pubKeyAttInfo
    )
    baseCheckWithAtt(msgInfo, pubKeyAttInfo)

    val pgpKeyDetails =
      PrivateKeysManager.getPgpKeyDetailsFromAssets("pgp/denbond7@flowcrypt.test_pub_primary.asc")
    val email = requireNotNull(pgpKeyDetails.getPrimaryInternetAddress()).address
    onView(withId(R.id.textViewKeyOwnerTemplate)).check(
      matches(withText(getResString(R.string.template_message_part_public_key_owner, email)))
    )

    onView(withId(R.id.textViewFingerprintTemplate)).check(
      matches(
        withText(
          getHtmlString(
            getResString(
              R.string.template_message_part_public_key_fingerprint,
              GeneralUtil.doSectionsInText(" ", pgpKeyDetails.fingerprint, 4)!!
            )
          )
        )
      )
    )

    onView(withId(R.id.textViewManualImportWarning)).check(
      matches(withText(getResString(R.string.warning_about_manual_import, email)))
    )

    val block = msgInfo?.msgBlocks?.get(1) as PublicKeyMsgBlock

    onView(withId(R.id.textViewPgpPublicKey))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.switchShowPublicKey))
      .check(matches(not(isChecked())))
      .perform(scrollTo(), click())
    onView(withId(R.id.textViewPgpPublicKey))
      .check(matches(isDisplayed()))
    onView(withId(R.id.textViewPgpPublicKey))
      .check(matches(withText(TestGeneralUtil.replaceVersionInKey(block.keyDetails?.publicKey))))
    onView(withId(R.id.switchShowPublicKey))
      .check(matches(isChecked()))
      .perform(scrollTo(), click())
    onView(withId(R.id.textViewPgpPublicKey))
      .check(matches(not(isDisplayed())))

    onView(withId(R.id.buttonKeyAction))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())
    onView(withId(R.id.buttonKeyAction))
      .check(matches(not(isDisplayed())))
  }

  @Test
  fun test8bitEncodingUtf8() {
    baseCheck(
      getMsgInfo(
        "messages/info/msg_info_8bit-utf8.json",
        "messages/mime/8bit-utf8.txt"
      )
    )
  }

  @Test
  fun testToLabelForTwoRecipients() {
    baseCheck(
      getMsgInfo(
        "messages/info/standard_msg_info_plaintext_to_2_recipients.json",
        "messages/mime/standard_msg_info_plaintext_to_2_recipients.txt"
      )
    )

    val subText = getResString(R.string.me) + ", User"

    onView(withId(R.id.tVTo))
      .check(matches(withText(getResString(R.string.to_receiver, subText))))
  }

  @Test
  fun testMsgDetailsSingleToReplyToCC() {
    val msgInfo = getMsgInfo(
      "messages/info/standard_msg_info_plaintext_single_to_replyto_cc.json",
      "messages/mime/standard_msg_info_plaintext_single_to_replyto_to_cc.txt"
    )
    baseCheck(msgInfo)

    onView(withId(R.id.rVMsgDetails))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.iBShowDetails))
      .perform(scrollTo(), click())
    onView(withId(R.id.rVMsgDetails))
      .check(matches(isDisplayed()))
      .check(matches(withRecyclerViewItemCount(5)))

    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withHeaderInfo(
            MsgDetailsRecyclerViewAdapter.Header(
              name = getResString(R.string.from),
              value = "Denis Bondarenko <denbond7@flowcrypt.test>"
            )
          )
        )
      )
    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withHeaderInfo(
            MsgDetailsRecyclerViewAdapter.Header(
              name = getResString(R.string.reply_to),
              value = "Denis Bondarenko <denbond7@flowcrypt.test>"
            )
          )
        )
      )
    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withHeaderInfo(
            MsgDetailsRecyclerViewAdapter.Header(
              name = getResString(R.string.to),
              value = "default@flowcrypt.test"
            )
          )
        )
      )
    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withHeaderInfo(
            MsgDetailsRecyclerViewAdapter.Header(
              name = getResString(R.string.cc),
              value = "ccuser@test"
            )
          )
        )
      )

    val flags = DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or
        DateUtils.FORMAT_SHOW_YEAR
    val datetime = DateUtils.formatDateTime(
      getTargetContext(),
      msgInfo?.msgEntity?.receivedDate ?: 0, flags
    )

    onView(withId(R.id.rVMsgDetails))
      .perform(
        scrollToHolder(
          withHeaderInfo(
            MsgDetailsRecyclerViewAdapter.Header(
              name = getResString(R.string.date),
              value = datetime
            )
          )
        )
      )
  }

  @Test
  //more details here https://github.com/FlowCrypt/flowcrypt-android/issues/1475
  fun testEncryptedMsgHiddenAttPGPMimeModifiedByGoogle() {
    val attInfo = TestGeneralUtil.getObjectFromJson(
      "messages/attachments/hidden_att_pgp_mime_modified_by_google.json",
      AttachmentInfo::class.java
    )

    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_hidden_att_pgp_mime_modified_by_google.json",
      "messages/mime/encrypted_msg_hidden_att_pgp_mime_modified_by_google.txt", attInfo
    )
    baseCheck(msgInfo)
    onView(withId(R.id.rVAttachments))
      .check(matches(withEmptyRecyclerView()))
      .check(matches(not(isDisplayed())))
  }

  @Test
  fun testEncryptedSymantecEncryptionServerMessageFormat() {
    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_symantec_encryption_server_message_format.json",
      "messages/mime/encrypted_msg_symantec_encryption_server_message_format.txt"
    )
    baseCheck(msgInfo)
  }

  @Test
  fun testShowParsePubKeyError() {
    val msgInfo = getMsgInfo(
      "messages/info/encrypted_msg_inline_pub_key_parse_error.json",
      "messages/mime/encrypted_msg_inline_pub_key_parse_error.txt"
    ) ?: throw NullPointerException()

    assertThat(msgInfo, notNullValue())

    val details = msgInfo.msgEntity

    launchActivity(details)
    matchHeader(msgInfo)

    val block = msgInfo.msgBlocks?.get(1) as PublicKeyMsgBlock
    val errorMsg = getResString(
      R.string.msg_contains_not_valid_pub_key, requireNotNull(block.error?.errorMsg)
    )
    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(errorMsg)))
    testSwitch(block.content ?: "")
    matchReplyButtons(details)
  }

  @Test
  fun testSignedArmoredMsg() {
    val msgInfo = getMsgInfo(
      "messages/info/signed_msg_armored.json",
      "messages/mime/signed_msg_armored.txt"
    )
    baseCheck(msgInfo)
  }

  @Test
  fun testSignedMsgClearSign() {
    val msgInfo = getMsgInfo(
      "messages/info/signed_msg_clearsign.json",
      "messages/mime/signed_msg_clearsign.txt"
    )
    baseCheck(msgInfo)
  }

  @Test
  fun testSignedMsgClearSignBroken() {
    val msgInfo = getMsgInfo(
      "messages/info/signed_msg_clearsign_broken.json",
      "messages/mime/signed_msg_clearsign_broken.txt"
    ) ?: throw NullPointerException()

    assertThat(msgInfo, notNullValue())

    val details = msgInfo.msgEntity

    launchActivity(details)
    matchHeader(msgInfo)

    val block = msgInfo.msgBlocks?.get(1) as GenericMsgBlock
    val errorMsg = getResString(
      R.string.msg_contains_not_valid_block,
      block.type.toString(),
      requireNotNull(block.error?.errorMsg)
    )
    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(errorMsg)))
    matchReplyButtons(details)
  }

  @Test
  fun testMsgWithKeyThatHasNoSuitableEncryptionSubKeys() {
    val msgInfo = getMsgInfo(
      "messages/info/standard_msg_with_pub_key_that_has_no_suitable_encryption_subkeys.json",
      "messages/mime/standard_msg_with_pub_key_that_has_no_suitable_encryption_subkeys.txt"
    )
    baseCheck(msgInfo)

    onView(allOf(withId(R.id.textViewStatus), hasSibling(withId(R.id.switchShowPublicKey))))
      .check(matches(withText(getResString(R.string.cannot_be_used_for_encryption))))
    onView(withId(R.id.buttonKeyAction))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.textViewManualImportWarning))
      .check(matches(not(isDisplayed())))
  }

  @Test
  fun testSignatureVerificationInbandMissingPubKeyEncryptedAndSigned() {
    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_missing_pub_key_encrypted_signed.json",
      "messages/mime/signature_verification_inband_missing_pub_key_encrypted_signed.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.CAN_NOT_VERIFY_SIGNATURE
    )
  }

  @Test
  fun testSignatureVerificationInbandMissingPubKeyOnlySigned() {
    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_missing_pub_key_only_signed.json",
      "messages/mime/signature_verification_inband_missing_pub_key_only_signed.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.CAN_NOT_VERIFY_SIGNATURE
    )
  }

  @Test
  fun testSignatureVerificationInbandOnlySignedMixed() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_second.asc")
    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_only_signed_mixed.json",
      "messages/mime/signature_verification_inband_only_signed_mixed.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.MIXED_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandEncryptedAndSignedMixed() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_second.asc")
    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_encrypted_signed_mixed.json",
      "messages/mime/signature_verification_inband_encrypted_signed_mixed.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.MIXED_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandOnlySignedPartially() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_only_signed_partially.json",
      "messages/mime/signature_verification_inband_only_signed_partially.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.ONLY_PARTIALLY_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandEncryptedSignedPartially() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_encrypted_signed_partially.json",
      "messages/mime/signature_verification_inband_encrypted_signed_partially.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.ONLY_PARTIALLY_SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandEncryptedSigned() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_encrypted_signed.json",
      "messages/mime/signature_verification_inband_encrypted_signed.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandOnlySigned() {
    PrivateKeysManager.savePubKeyToDatabase("pgp/denbond7@flowcrypt.test_pub_primary.asc")

    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_only_signed.json",
      "messages/mime/signature_verification_inband_only_signed.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.SIGNED
    )
  }

  @Test
  fun testSignatureVerificationInbandOnlyEncrypted() {
    val msgInfo = getMsgInfo(
      "messages/info/signature_verification_inband_only_encrypted.json",
      "messages/mime/signature_verification_inband_only_encrypted.txt"
    )
    baseCheck(msgInfo)

    testPgpBadges(
      2,
      PgpBadgeListAdapter.PgpBadge.Type.ENCRYPTED,
      PgpBadgeListAdapter.PgpBadge.Type.NOT_SIGNED
    )
  }

  private fun testMissingKey(incomingMsgInfo: IncomingMessageInfo?) {
    assertThat(incomingMsgInfo, notNullValue())

    val details = incomingMsgInfo!!.msgEntity

    launchActivity(details)
    matchHeader(incomingMsgInfo)

    val block = incomingMsgInfo.msgBlocks?.get(1) as DecryptErrorMsgBlock
    val errorMsg = getResString(R.string.decrypt_error_current_key_cannot_open_message)

    onView(withId(R.id.textViewErrorMessage))
      .check(matches(withText(errorMsg)))

    testSwitch(block.content ?: "")
    matchReplyButtons(details)
  }

  private fun testSwitch(content: String) {
    onView(withId(R.id.textViewOrigPgpMsg))
      .check(matches(not(isDisplayed())))
    onView(withId(R.id.switchShowOrigMsg))
      .check(matches(not(isChecked())))
      .perform(scrollTo(), click())
    onView(withId(R.id.textViewOrigPgpMsg))
      .check(matches(isDisplayed()))
    onView(withId(R.id.textViewOrigPgpMsg))
      .check(matches(withText(content)))
    onView(withId(R.id.switchShowOrigMsg))
      .check(matches(isChecked()))
      .perform(scrollTo(), click())
    onView(withId(R.id.textViewOrigPgpMsg))
      .check(matches(not(isDisplayed())))
  }

  private fun baseCheck(incomingMsgInfo: IncomingMessageInfo?) {
    assertThat(incomingMsgInfo, notNullValue())

    val details = incomingMsgInfo!!.msgEntity
    launchActivity(details)
    matchHeader(incomingMsgInfo)

    checkWebViewText(incomingMsgInfo.text)
    matchReplyButtons(details)
  }

  private fun baseCheckWithAtt(incomingMsgInfo: IncomingMessageInfo?, att: AttachmentInfo?) {
    assertThat(incomingMsgInfo, notNullValue())

    val msgEntity = incomingMsgInfo!!.msgEntity
    launchActivity(msgEntity)
    matchHeader(incomingMsgInfo)

    checkWebViewText(incomingMsgInfo.text)
    onView(withId(R.id.layoutAtt))
      .check(matches(isDisplayed()))
    matchAtt(att)
    matchReplyButtons(msgEntity)
  }

  private fun matchHeader(incomingMsgInfo: IncomingMessageInfo?) {
    val msgEntity = incomingMsgInfo?.msgEntity
    requireNotNull(msgEntity)

    onView(withId(R.id.textViewSenderAddress))
      .check(matches(withText(EmailUtil.getFirstAddressString(msgEntity.from))))
    onView(withId(R.id.textViewDate))
      .check(
        matches(
          withText(
            DateTimeUtil.formatSameDayTime(
              getTargetContext(),
              msgEntity.receivedDate
            )
          )
        )
      )
    onView(withId(R.id.textViewSubject))
      .check(matches(anyOf(withText(msgEntity.subject), withText(incomingMsgInfo.inlineSubject))))
  }

  private fun matchAtt(att: AttachmentInfo?) {
    requireNotNull(att)
    onView(withId(R.id.textViewAttachmentName))
      .check(matches(withText(att.name)))
    onView(withId(R.id.textViewAttSize))
      .check(matches(withText(Formatter.formatFileSize(getContext(), att.encodedSize))))
  }

  private fun matchReplyButtons(msgEntity: MessageEntity) {
    onView(withId(R.id.imageButtonReplyAll))
      .check(matches(isDisplayed()))
    onView(withId(R.id.layoutReplyButton))
      .check(matches(isDisplayed()))
    onView(withId(R.id.layoutReplyAllButton))
      .check(matches(isDisplayed()))
    onView(withId(R.id.layoutFwdButton))
      .check(matches(isDisplayed()))

    if (msgEntity.isEncrypted == true) {
      onView(withId(R.id.textViewReply))
        .check(matches(withText(getResString(R.string.reply_encrypted))))
      onView(withId(R.id.textViewReplyAll))
        .check(matches(withText(getResString(R.string.reply_all_encrypted))))
      onView(withId(R.id.textViewFwd))
        .check(matches(withText(getResString(R.string.forward_encrypted))))

      onView(withId(R.id.imageViewReply))
        .check(matches(withDrawable(R.mipmap.ic_reply_green)))
      onView(withId(R.id.imageViewReplyAll))
        .check(matches(withDrawable(R.mipmap.ic_reply_all_green)))
      onView(withId(R.id.imageViewFwd))
        .check(matches(withDrawable(R.mipmap.ic_forward_green)))
    } else {
      onView(withId(R.id.textViewReply))
        .check(matches(withText(getResString(R.string.reply))))
      onView(withId(R.id.textViewReplyAll))
        .check(matches(withText(getResString(R.string.reply_all))))
      onView(withId(R.id.textViewFwd))
        .check(matches(withText(getResString(R.string.forward))))

      onView(withId(R.id.imageViewReply))
        .check(matches(withDrawable(R.mipmap.ic_reply_red)))
      onView(withId(R.id.imageViewReplyAll))
        .check(matches(withDrawable(R.mipmap.ic_reply_all_red)))
      onView(withId(R.id.imageViewFwd))
        .check(matches(withDrawable(R.mipmap.ic_forward_red)))
    }
  }

  private fun testTopReplyAction(title: String) {
    testStandardMsgPlaintext()

    onView(withId(R.id.imageButtonMoreOptions))
      .check(matches(isDisplayed()))
      .perform(scrollTo(), click())

    onView(withText(title))
      .inRoot(RootMatchers.isPlatformPopup())
      .perform(click())

    intended(hasComponent(CreateMessageActivity::class.java.name))

    onView(withId(R.id.toolbar))
      .check(matches(CustomMatchers.withToolBarText(title)))
  }

  private fun withHeaderInfo(header: MsgDetailsRecyclerViewAdapter.Header):
      Matcher<RecyclerView.ViewHolder> {
    return object : BoundedMatcher<RecyclerView.ViewHolder,
        MsgDetailsRecyclerViewAdapter.ViewHolder>(
      MsgDetailsRecyclerViewAdapter.ViewHolder::class.java
    ) {
      override fun matchesSafely(holder: MsgDetailsRecyclerViewAdapter.ViewHolder): Boolean {
        return holder.tVHeaderName.text.toString() == header.name
            && holder.tVHeaderValue.text.toString() == header.value
      }

      override fun describeTo(description: Description) {
        description.appendText("with: $header")
      }
    }
  }

  private fun testPgpBadges(badgeCount: Int, vararg badgeTypes: PgpBadgeListAdapter.PgpBadge.Type) {
    onView(withId(R.id.rVPgpBadges))
      .check(matches(withRecyclerViewItemCount(badgeCount)))


    for (badgeType in badgeTypes) {
      onView(withId(R.id.rVPgpBadges))
        .perform(scrollToHolder(withPgpBadge(PgpBadgeListAdapter.PgpBadge(badgeType))))
    }
  }
}
