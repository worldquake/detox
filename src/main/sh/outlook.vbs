' Create item types
Const olMailItem = 0

' Available recipient types
Const olRecipientTypeTO =1
Const olRecipientTypeCC =2
Const olRecipientTypeBCC = 3
Const olByValue = 1
 
Const cstUtf8 = 65001 

Const olFormatHTML     = 2
Const olFormatPlain    = 1
Const olFormatRichText = 3
Const olFormatUnspecified = 0

Const olFolderInbox = 6
Const olMail = 43

' Save formats
Const olMHTML = 1027
Const olMSG = 3

Const PR_ATTACH_MIME_TAG = "http://schemas.microsoft.com/mapi/proptag/0x370E001E"
Const PR_ATTACH_CONTENT_ID = "http://schemas.microsoft.com/mapi/proptag/0x3712001E"

' Global variables
Set objOutlook = CreateObject("Outlook.Application")
Set objNamespace = objOutlook.GetNamespace("MAPI")
Set objFilesys = CreateObject("Scripting.FileSystemObject") 
Set stream = CreateObject("ADODB.Stream")
stream.Type = 2 'text
stream.Charset = "utf-8"

' Helper functions for operations
function readIn(file)
	stream.Open
	stream.LoadFromFile file
	readIn = stream.ReadText
	stream.Close
end function

' Callable functions for outlook operations
' arg[1]: Search string
' arg[2]: Save to file (msg format)
' arg[3]: What to save to the target file: 0 = body, 1 ... attachment number to save
' arg[4]: Wait time (defaults to 2000)
function outlook_receive()
	Set items = objNamespace.GetDefaultFolder(olFolderInbox).Items
	items.Sort "[CreationTime]", True
	search = WScript.Arguments.Item(1)
	file = WScript.Arguments.Item(2)
	If objFilesys.FileExists(file) Then objFilesys.DeleteFile file
	what = 0
	If Wscript.Arguments.Count > 3 Then what = Int(WScript.Arguments.Item(3))
	sleep = 2000
	If Wscript.Arguments.Count > 4 Then sleep = Int(WScript.Arguments.Item(4))
	outlook_receive = Null
	For i = 1 To 5
		limit = 5
		For Each item In items
			If item.Class = olMail And (InStr(1, item.Subject, search) > 0 Or InStr(1, item.SenderName, search) > 0) Then
				outlook_receive = item.SenderName & " - " &item.CreationTime & ": " & item.Subject
				If Wscript.Arguments.Count > 2 Then
					If what = 0 Then
						stream.Open
						stream.WriteText item.Body
						stream.SaveToFile file, 2
					Else
						item.Attachments(what).SaveAsFile file
					End If
				Else
					WScript.Echo outlook_receive
				End If
				Exit For
			End If
			limit = limit - 1
			If limit = 0 Then Exit For
		Next
		If Not IsNull(outlook_receive) Then Exit For
		WScript.Sleep i * sleep
	Next
end function

' Callable functions for outlook operations
' arg[1]  : Subject
' arg[2..]: Recipients, format: "Name|email@host.com[:CC|TO|BCC)" (Type of recipient is optional)
' StdIn   : List of files to attach, first is always the message body
function outlook_send()
	inf = WScript.StdIn.ReadLine
	fn = objFilesys.GetFileName(inf)
	Set mail = Nothing
	rel = 1
	if Left(fn,7) = "related" then
		rel = 0
	end if
	if Right(fn,3) = "msg" then
		Set mail = objOutlook.CreateItemFromTemplate(inf)
	else
		Set mail = objOutlook.CreateItem(olMailItem)
		mail.InternetCodePage = cstUtf8
		if Right(fn,3) = "tml" then
			mail.BodyFormat = olFormatHTML
			mail.HTMLBody = readIn(inf)
		else
			mail.BodyFormat = olFormatPlain
			mail.Body = readIn(inf)
		end if
	end if
	With mail
		.InternetCodePage = cstUtf8
		For recptIdx = 2 to WScript.Arguments.Count - 1
			rcptArr = Split(WScript.Arguments.Item(recptIdx), ":")
			Set recpt = .Recipients.Add(rcptArr(0))
			if UBound(rcptArr) = 1 then
				recpt.Type = eval("olRecipientType" & UCase(rcptArr(1)))
			end if
		Next
		.Subject = WScript.Arguments.Item(1)
		.ReadReceiptRequested = False
		Do While Not WScript.StdIn.AtEndOfStream
			.Attachments.Add WScript.StdIn.ReadLine, olByValue, rel
		Loop
		If rel = 0 Then ' Hidden attachments means references
			For Each oAttach In .Attachments
				name = objFilesys.GetFileName(oAttach.FileName)
				ext = LCASE(objFilesys.GetExtensionName(oAttach.FileName))
				Set oPA = oAttach.PropertyAccessor
				Select Case ext
					Case "png" ext = "image/png"
					Case "gif" ext = "image/gif"
					Case Else ext = "image/jpeg"
				End Select
				'WScript.StdErr.Write("EXT=" & ext & ", n=" & name)
				oPA.SetProperty PR_ATTACH_MIME_TAG, ext
				oPA.SetProperty PR_ATTACH_CONTENT_ID, name
			Next
		End If
		mail.Send
		'WScript.StdErr.Write(inf & " BF=" & mail.BodyFormat & " ,rel=" & rel & " ,r=" & Left(fn,7))
	End With
end function

' Main application code simply calls a method
func = WScript.Arguments.Item(0)
eval("outlook_" & func & "()")
' End of main

Set objNamespace = Nothing
Set objOutlook   = Nothing
