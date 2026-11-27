/**
 * Creates the SHORT Shomer survey (5 questions, Hebrew) as a Google Form, with answers going to a new Google Sheet.
 * This is the wording of the form that was actually sent (2026-11-27).
 * How to run (once, about 2 minutes):
 *   1. Open https://script.google.com and click "New project".
 *   2. Delete the sample code, paste this whole file, and click Save.
 *   3. Choose the function createShortSurvey in the toolbar and click Run.
 *   4. Approve the permissions when Google asks (it is your own account).
 *   5. Open the Execution log: it prints the link to SEND to respondents.
 *   6. In the form, click Share, and set responders to "Anyone with the link". Leave editing restricted to you.
 * The form is anonymous: it does not collect email addresses. Answers go to a sheet named "תשובות סקר שומר (קצר)" in your Drive.
 * The sheet stays private. Publish totals only, not each person's answers.
 */
function createShortSurvey() {
  var form = FormApp.create('כמה שאלות על הודעות חשודות בטלפון');
  form.setDescription(
    'שלום,\n' +
    'אני בונה פרויקט שעוזר לבדוק הודעות חשודות בטלפון, ואשמח לשמוע מכם.\n\n' +
    'יש כאן חמש שאלות קצרות, וזה לוקח פחות מדקה.\n' +
    'אין תשובה נכונה או לא נכונה, ואפשר לדלג על כל שאלה.\n' +
    'לא שואלים שם ולא מספר טלפון.\n\n' +
    'תודה רבה!');
  form.setCollectEmail(false);
  form.setAllowResponseEdits(false);
  form.setProgressBar(false);
  form.setConfirmationMessage('תודה רבה! התשובות שלכם עוזרות לי מאוד.');

  function radio(title, help, choices) {
    var item = form.addMultipleChoiceItem().setTitle(title).setChoiceValues(choices).setRequired(false);
    if (help) item.setHelpText(help);
  }
  function checkboxes(title, help, choices) {
    var item = form.addCheckboxItem().setTitle(title).setChoiceValues(choices).setRequired(false);
    if (help) item.setHelpText(help);
  }

  radio('בני כמה אתם?', '', ['עד 30', '30 עד 40', '40 עד 75', 'מעל 75']);
  radio('איזה טלפון יש לכם?', '', ['אנדרואיד (סמסונג, שיאומי ועוד)', 'אייפון', 'טלפון רגיל, בלי אינטרנט', 'לא יודע']);
  checkboxes('בחצי השנה האחרונה, האם קיבלתם הודעה חשודה?',
    'למשל הודעה שנראית כאילו היא מדואר ישראל, מהבנק, על קנס או על החזר כספי. ומה עשיתם? אפשר לסמן כמה תשובות.',
    ['לא קיבלתי הודעה כזו', 'התעלמתי', 'שאלתי מישהו מהמשפחה או חבר', 'בדקתי בעצמי באתר או באפליקציה הרשמית',
     'שלחתי את ההודעה לצ׳אט (קלוד, ג׳מיני וכו׳) ושאלתי אם הקישור תקין', 'לחצתי על הקישור', 'מסרתי פרטים או שילמתי']);
  radio('אם הטלפון היה מזהיר אתכם בעצמו, איזו אזהרה הייתה הכי ברורה לכם?', '',
    ['"אל תלחצו על הקישור. הוא לא מוביל לאתר של דואר ישראל."', '"הודעה מסוכנת"', '"זו הונאה"', 'משהו אחר']);
  radio('אם מגיעה הודעה שנראית כמו הונאה, הייתם רוצים שמישהו מהמשפחה יקבל על כך התראה?', '',
    ['כן, בכל פעם', 'כן, אבל רק כשברור שיש סכנה', 'לא, אני מסתדר', 'לא יודע']);

  var sheet = SpreadsheetApp.create('תשובות סקר שומר (קצר)');
  form.setDestination(FormApp.DestinationType.SPREADSHEET, sheet.getId());
  Logger.log('קישור לשליחה: ' + form.getPublishedUrl());
  Logger.log('קישור לעריכה: ' + form.getEditUrl());
  Logger.log('טבלת התשובות: ' + sheet.getUrl());
}
