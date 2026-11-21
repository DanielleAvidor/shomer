/**
 * Creates the Shomer research survey as a Google Form, with responses collected in a new Google Sheet.
 * How to run (once, about 2 minutes):
 *   1. Open https://script.google.com and click "New project".
 *   2. Delete the sample code, paste this whole file, and click Save.
 *   3. Choose the function createShomerSurvey in the toolbar and click Run.
 *   4. Approve the permissions when Google asks (it is your own account).
 *   5. Open the Execution log: it prints the link to SEND to respondents and the link to EDIT the form.
 * The form is anonymous: it does not collect email addresses.
 */
function createShomerSurvey() {
  var form = FormApp.create('סקר קצר על הודעות חשודות');
  form.setDescription(
    'לוקח כ-3 דקות. אין תשובות נכונות או לא נכונות, ואפשר לדלג על כל שאלה. ' +
    'הסקר אנונימי: בלי שם ובלי מספר טלפון.');
  form.setCollectEmail(false);
  form.setAllowResponseEdits(false);
  form.setConfirmationMessage('תודה רבה! התשובות שלכם עוזרות מאוד.');

  function radio(title, choices) {
    form.addMultipleChoiceItem().setTitle(title).setChoiceValues(choices).setRequired(false);
  }
  function boxes(title, choices) {
    form.addCheckboxItem().setTitle(title).setChoiceValues(choices).setRequired(false);
  }
  function text(title, help) {
    var item = form.addParagraphTextItem().setTitle(title).setRequired(false);
    if (help) item.setHelpText(help);
  }

  radio('מי ממלא?', ['אני בעצמי', 'אני ממלא/ת בשם קרוב משפחה']);
  radio('גיל', ['עד 40', '41 עד 60', '61 עד 75', '76 ומעלה']);
  radio('באיזה טלפון משתמשים?',
    ['אנדרואיד (סמסונג, שיאומי ועוד)', 'אייפון', 'טלפון רגיל, בלי אינטרנט', 'לא יודע/ת']);
  boxes('איפה מקבלים הודעות? (אפשר כמה)', ['הודעות SMS רגילות', 'וואטסאפ', 'מייל']);
  radio('בחצי השנה האחרונה, קיבלתם הודעה חשודה?',
    ['לא', 'פעם אחת', 'כמה פעמים', 'הרבה, כל שבוע']);
  boxes('על מה הייתה ההודעה? (אפשר כמה)',
    ['חבילה או דואר', 'בנק או כרטיס אשראי', 'קנס או כביש אגרה',
     'מישהו מהמשפחה עם מספר חדש', 'זכייה או החזר כספי', 'משהו אחר']);
  radio('מה עשיתם?',
    ['התעלמתי', 'שאלתי מישהו מהמשפחה או חבר', 'לחצתי על הקישור', 'מסרתי פרטים',
     'שילמתי או העברתי כסף', 'לא קיבלתי הודעה כזו']);

  form.addScaleItem()
    .setTitle('עד כמה אתם בטוחים שתזהו הודעה מזויפת?')
    .setBounds(1, 5).setLabels('בכלל לא', 'בטוח/ה מאוד').setRequired(false);

  radio('היית מדביק/ה הודעה חשודה באפליקציה כדי שתבדוק אותה?', ['כן', 'אולי', 'לא']);
  text('אם "אולי" או "לא", מה מפריע?');
  radio('אם מגיעה אליכם הודעה שנראית כמו הונאה, היה לכם שימושי שבן משפחה יקבל על כך התראה?',
    ['כן, בכל פעם', 'כן, אבל רק אם יש סכנה ממשית', 'לא צריך, אני מסתדר/ת', 'לא יודע/ת']);
  text('מה היה גורם לכם לסמוך על אפליקציה כזו?');
  text('רוצים לעזור יותר? הדביקו הודעה חשודה אמיתית שקיבלתם (לא חובה)',
    'לפני ההדבקה מחקו שמות, מספרי טלפון ומספרי חשבון. אפשר להשאיר את הקישור.');

  var sheet = SpreadsheetApp.create('תשובות סקר שומר');
  form.setDestination(FormApp.DestinationType.SPREADSHEET, sheet.getId());

  Logger.log('קישור לשליחה לממלאים: ' + form.getPublishedUrl());
  Logger.log('קישור לעריכת הטופס: ' + form.getEditUrl());
  Logger.log('טבלת התשובות: ' + sheet.getUrl());
}
