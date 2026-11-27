/**
 * Creates the SHORT Shomer survey (5 questions) as a Google Form, with answers collected in a new Google Sheet.
 * How to run (once, about 2 minutes):
 *   1. Open https://script.google.com and click "New project".
 *   2. Delete the sample code, paste this whole file, and click Save.
 *   3. Choose the function createShortSurvey in the toolbar and click Run.
 *   4. Approve the permissions when Google asks (it is your own account).
 *   5. Open the Execution log: it prints the link to SEND to respondents.
 * The form is anonymous: it does not collect email addresses. Answers go to a sheet named "תשובות סקר שומר (קצר)" in your Drive.
 */
function createShortSurvey() {
  var form = FormApp.create('חמש שאלות על הודעות חשודות');
  form.setDescription(
    'לוקח פחות מדקה. אין תשובות נכונות או לא נכונות, ואפשר לדלג על כל שאלה. ' +
    'הסקר אנונימי: בלי שם ובלי מספר טלפון. התשובות משמשות לפרויקט לימודי.');
  form.setCollectEmail(false);
  form.setAllowResponseEdits(false);
  form.setConfirmationMessage('תודה רבה! התשובות שלכם עוזרות מאוד.');

  function radio(title, choices) {
    form.addMultipleChoiceItem().setTitle(title).setChoiceValues(choices).setRequired(false);
  }

  radio('מי ממלא?', ['אני בעצמי, בן/בת 60 ומעלה', 'אני ממלא/ת בשם קרוב משפחה מבוגר', 'אני בן/בת משפחה של אדם מבוגר ועונה על עצמי']);
  radio('באיזה טלפון משתמשים?', ['אנדרואיד', 'אייפון', 'טלפון רגיל, בלי אינטרנט', 'לא יודע/ת']);
  radio('בחצי השנה האחרונה, קיבלתם הודעה חשודה (דואר, בנק, קנס, החזר כספי)? מה עשיתם?',
    ['לא קיבלתי', 'התעלמתי', 'שאלתי מישהו מהמשפחה או חבר', 'בדקתי לבד באתר או באפליקציה הרשמית', 'לחצתי על הקישור', 'מסרתי פרטים או שילמתי']);
  radio('אם הטלפון היה מזהיר אתכם לבד, איזה נוסח היה הכי ברור?',
    ['"אל תלחצו על הקישור. הוא לא מוביל לאתר של דואר ישראל."', '"הודעה מסוכנת"', '"זו הונאה"', 'משהו אחר, אשמח לכתוב']);
  radio('אם מגיעה הודעה שנראית כמו הונאה, האם היה לכם שימושי שבן משפחה יקבל התראה?',
    ['כן, בכל פעם', 'כן, אבל רק אם יש סכנה ודאית', 'לא צריך, אני מסתדר/ת', 'לא יודע/ת']);

  var sheet = SpreadsheetApp.create('תשובות סקר שומר (קצר)');
  form.setDestination(FormApp.DestinationType.SPREADSHEET, sheet.getId());
  Logger.log('קישור לשליחה: ' + form.getPublishedUrl());
  Logger.log('קישור לעריכה: ' + form.getEditUrl());
  Logger.log('טבלת התשובות: ' + sheet.getUrl());
}
